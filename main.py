import os
import time
import shutil
import mimetypes
import asyncio
import logging
import re
import requests
import psutil
import subprocess
import math
from threading import Thread
from collections import deque
from shlex import quote
from urllib.parse import urlparse, unquote, parse_qs
from pathlib import Path
from pyrogram import Client, filters
from pyrogram.types import Message, InlineKeyboardMarkup, InlineKeyboardButton
from pyrogram.errors import FloodWait, MessageNotModified, MessageIdInvalid
from flask import Flask

# Load environment variables
API_ID = os.getenv("API_ID")
API_HASH = os.getenv("API_HASH")
BOT_TOKEN = os.getenv("BOT_TOKEN")

if not all([API_ID, API_HASH, BOT_TOKEN]):
    raise ValueError("API_ID, API_HASH, and BOT_TOKEN must be set in environment variables")

bot = Client("torrent_bot", api_id=API_ID, api_hash=API_HASH, bot_token=BOT_TOKEN)

DOWNLOAD_DIR = "downloads"
MAX_SIZE = 2000 * 1024 * 1024  # 2GB Telegram upload limit
MAX_CONCURRENT_DOWNLOADS = 1  # Single task per user
TIMEOUT = 1800  # 30 minutes

MAGNET_REGEX = r"^magnet:\?xt=urn:btih:[a-fA-F0-9]+"
TORRENT_REGEX = r"^https?://.*\.torrent(?:\?.*)?$"

# Logging setup with rotation
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    handlers=[logging.StreamHandler()]
)
op_logger = logging.getLogger("op_logger")
from logging.handlers import RotatingFileHandler
file_handler = RotatingFileHandler('bot.log', maxBytes=10*1024*1024, backupCount=5)
file_handler.setFormatter(logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s'))
op_logger.addHandler(file_handler)
op_logger.setLevel(logging.INFO)

# Flask health check
flask_app = Flask(__name__)
@flask_app.route('/')
def home():
    return "Bot is running ✅"
try:
    Thread(target=lambda: flask_app.run(host="0.0.0.0", port=8080), daemon=True).start()
except Exception as e:
    op_logger.error(f"Flask server failed to start: {str(e)}")

# Global process tracking
aria_processes = []

def human_readable_size(size, decimal_places=2):
    for unit in ["B", "KiB", "MiB", "GiB", "TiB"]:
        if size < 1024.0:
            break
        size /= 1024.0
    return f"{size:.{decimal_places}f} {unit}"

def progress_bar(percentage, bar_length=20):
    if not 0 <= percentage <= 100:
        percentage = max(0, min(100, percentage))
    filled = int(round(bar_length * percentage / 100))
    empty = bar_length - filled
    return "█" * filled + "░" * empty

async def safe_edit_message(message, text):
    try:
        await message.edit_text(text)
        return True
    except FloodWait as e:
        await asyncio.sleep(e.x)
        return await safe_edit_message(message, text)
    except (MessageNotModified, MessageIdInvalid):
        return True
    except Exception as e:
        op_logger.error(f"Error editing message: {str(e)}")
        return False

def split_large_file(file_path, chunk_size=MAX_SIZE):
    try:
        part_num = 1
        output_files = []
        base_name = os.path.basename(file_path)
        
        with open(file_path, 'rb') as f:
            while True:
                chunk = f.read(chunk_size)
                if not chunk:
                    break
                part_name = f"{base_name}.part{part_num:03d}"
                part_path = os.path.join(os.path.dirname(file_path), part_name)
                with open(part_path, 'wb') as p:
                    p.write(chunk)
                output_files.append(part_path)
                part_num += 1
        return output_files
    except Exception as e:
        op_logger.error(f"Error splitting file {file_path}: {str(e)}")
        return []

def clean_directory(directory):
    try:
        if os.path.exists(directory):
            shutil.rmtree(directory, ignore_errors=True)
        return True
    except Exception as e:
        op_logger.error(f"Error cleaning directory: {str(e)}")
        return False

def extract_thumbnail(video_path):
    try:
        if not shutil.which("ffmpeg"):
            op_logger.error("ffmpeg not found")
            return None
        thumbnail_path = os.path.join(os.path.dirname(video_path), "thumbnail.jpg")
        cmd = [
            "ffmpeg", "-ss", "00:00:10",
            "-i", video_path,
            "-vframes", "1",
            "-q:v", "2",
            thumbnail_path
        ]
        subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        return thumbnail_path if os.path.exists(thumbnail_path) else None
    except Exception as e:
        op_logger.error(f"Thumbnail extraction failed: {str(e)}")
        return None

def get_duration(file_path):
    try:
        if not shutil.which("ffprobe"):
            op_logger.error("ffprobe not found")
            return 0
        cmd = [
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file_path
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return int(float(result.stdout.strip()))
    except Exception:
        return 0

def sanitize_filename(filename):
    return re.sub(r'[\\/*?:"<>|]', "_", filename.strip())

def get_magnet_name(magnet_link):
    try:
        magnet_link = magnet_link.strip().replace(' ', '%20')
        params = parse_qs(urlparse(magnet_link).query)
        if 'dn' in params:
            return unquote(params['dn'][0])
        xt = params.get('xt', [''])[0]
        btih = xt.split('urn:btih:')[-1]
        return f"Torrent-{btih[:8].upper()}"
    except Exception:
        return "Unknown-Torrent"

def kill_aria_processes():
    global aria_processes
    for proc in aria_processes:
        try:
            proc.kill()
        except Exception as e:
            op_logger.error(f"Error killing aria2c process: {str(e)}")
    aria_processes.clear()

def time_formatter(seconds: float) -> str:
    if seconds < 0:
        seconds = 0
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{int(hours)}h {int(minutes)}m {int(seconds)}s"

def natural_sort_key(filename):
    """Sort filenames naturally, e.g., '1.mp3', '2.mp3', '10.mp3'."""
    return [int(c) if c.isdigit() else c.lower() for c in re.split(r'(\d+)', filename)]

user_queues = {}
user_messages = {}
active_downloads = {}
user_active_tasks = {}

@bot.on_message(filters.command("start") & (filters.private | filters.group))
async def start_handler(client, message):
    caption = (
        f"<pre>🌟 𝗧𝗼𝗿𝗿𝗲𝗻𝘁 𝗗𝗼𝘄𝗻𝗹𝗼𝗮𝗱𝗲𝗿 𝗕𝗼𝘁! 🌟</pre>\n\n"
        "<b>How to use:</b>\n"
        "Send a magnet link, .torrent URL, or .torrent file.\n"
        "I'll download and upload the files in order!\n"
        "<b>Note:</b> Only one task per user at a time.\n"
    )
    await message.reply(caption)

async def run_aria2c(command, msg, start_time, torrent_name):
    global aria_processes
    kill_aria_processes()
    user_id = msg.chat.id
    msg_id = msg.id

    process = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT
    )
    aria_processes.append(process)
    
    progress_regex = re.compile(
        r'\[#\w+\s+([\d.]+\w*)/([\d.]+\w*)\((\d+)%\)\s+.*DL:([\d.]+\w*).*ETA:(\w+)\]'
    )
    last_update = time.time()
    last_reported_percent = -1

    while True:
        if user_id in active_downloads and active_downloads[user_id] != msg_id:
            process.terminate()
            return False

        elapsed = time.time() - start_time
        if elapsed > TIMEOUT:
            process.terminate()
            await safe_edit_message(msg, "❌ Download timed out after 30 minutes!")
            return False

        try:
            line = await asyncio.wait_for(process.stdout.readline(), 1.0)
        except asyncio.TimeoutError:
            if process.returncode is not None:
                break
            continue

        if not line:
            if process.returncode is not None:
                break
            continue

        line = line.decode().strip()
        op_logger.debug(f"aria2c: {line}")

        if "Download complete:" in line:
            return True

        match = progress_regex.search(line)
        if match:
            downloaded = match.group(1)
            total = match.group(2)
            percentage = int(match.group(3))
            speed = match.group(4)
            eta = match.group(5)

            if time.time() - last_update > 5 or percentage == 100:
                bar = progress_bar(percentage)
                status = (
                    f"📩 **Downloading...**\n"
                    f"🪺 Torrent: `{torrent_name}`\n"
                    f"📦 Progress: {downloaded}/{total} ({percentage}%)\n"
                    f"🔸 {bar} 🔸\n"
                    f"🚀 Speed: {speed} | ⏳ ETA: {eta}\n"
                )
                await safe_edit_message(msg, status)
                last_update = time.time()
                last_reported_percent = percentage

    return await process.wait() == 0

async def process_torrent(user_id, link, msg):
    timestamp = int(time.time())
    USER_DIR = Path(DOWNLOAD_DIR) / f"user_{user_id}_{timestamp}"
    USER_DIR.mkdir(parents=True, exist_ok=True)

    extra_trackers = [
        "udp://tracker.openbittorrent.com:80",
        "udp://tracker.opentrackr.org:1337",
        "udp://9.rarbg.me:2710",
        "udp://9.rarbg.to:2710",
        "udp://tracker.tiny-vps.com:6969",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.internetwarriors.net:1337",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://exodus.desync.com:6969",
        "udp://tracker.moeking.me:6969",
        "udp://tracker.dler.org:6969",
        "udp://tracker.filemail.com:6969",
        "udp://public.popcorn-tracker.org:6969",
        "udp://explodie.org:6969",
        "udp://tracker.torrent.eu.org:451",
        "udp://tracker.cyberia.is:6969",
        "udp://p4p.arenabg.com:1337",
        "udp://tracker.bt4g.com:6969/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        "udp://tracker.ccc.de:80",
        "udp://denis.stalker.upeer.me:6969/announce"
    ]

    start_time = time.time()
    torrent_name = "Unknown"

    try:
        if link.startswith("magnet:?"):
            torrent_name = get_magnet_name(link)
            await safe_edit_message(msg, f"\n📥 Starting download for {torrent_name}...")
            download_link = link
        else:  # .torrent file (URL or local)
            if link.lower().startswith('http'):
                await safe_edit_message(msg, "📥 Downloading torrent file...")
                response = requests.get(link, headers={'User-Agent': 'Mozilla/5.0'}, timeout=30)
                response.raise_for_status()
                filename = unquote(re.findall(r'filename\*?=[\'"]?(?:UTF-\d[\'"]*)?([^;\'"]+)', 
                                            response.headers.get("content-disposition", ""), 
                                            re.IGNORECASE)[0] or os.path.basename(urlparse(link).path))
                if not filename.lower().endswith('.torrent'):
                    filename += '.torrent'
                filename = sanitize_filename(filename)
                torrent_file_path = USER_DIR / filename
                with open(torrent_file_path, 'wb') as f:
                    f.write(response.content)
                torrent_name = filename.replace('.torrent', '')
                download_link = str(torrent_file_path)
            else:  # Local .torrent file
                filename = os.path.basename(link)
                torrent_name = filename.replace('.torrent', '')
                download_link = link
                await safe_edit_message(msg, f"📥 Torrent file ready: {torrent_name}\n🚀 Starting content download...")

        cmd = [
            "aria2c",
            "--console-log-level=info",
            "--enable-color=false",
            "--console-log-level=notice",
            "--log-level=warn",
            "--allow-overwrite=true",
            "--check-certificate=false",
            "--auto-file-renaming=true",
            "--file-allocation=none",
            "--enable-dht=true",
            "--bt-enable-lpd=true",
            "--bt-save-metadata=true",
            "--seed-time=0",
            "--max-connection-per-server=16",
            "--bt-tracker=" + ",".join(extra_trackers),
            "--split=16",
            "--max-concurrent-downloads=5",
            "--file-allocation=none",
            "--summary-interval=1",
            f"--dir={USER_DIR}",
            download_link
        ]

        op_logger.info(f"Starting download: {' '.join(cmd)}")
        download_success = await run_aria2c(cmd, msg, start_time, torrent_name)
        
        if not download_success:
            await safe_edit_message(msg, "❌ Download failed or canceled")
            return False

        # Find and sort downloaded files
        files = []
        for file_path in USER_DIR.rglob("*"):
            if (file_path.is_file() and 
                file_path.stat().st_size > 1024 and
                not file_path.suffix.lower() in ('.aria2', '.tmp', '.torrent')):
                files.append(file_path)
        
        if not files:
            await safe_edit_message(msg, "❌ No files found after download")
            return False

        # Sort files naturally
        files.sort(key=lambda x: natural_sort_key(x.name))

        await safe_edit_message(msg, f"📁 Found {len(files)} files. Starting uploads...")
        
        for file_path in files:
            filename = file_path.name
            size = file_path.stat().st_size

            if size > MAX_SIZE:
                await safe_edit_message(msg, f"⚠️ Splitting large file: {filename}")
                file_parts = split_large_file(str(file_path))
                file_path.unlink()  # Remove original file
            else:
                file_parts = [str(file_path)]

            total_parts = len(file_parts)
            for part_index, part in enumerate(file_parts, 1):
                part_name = os.path.basename(part)
                part_size = os.path.getsize(part)
                
                await safe_edit_message(
                    msg,
                    f"📤 **Uploading...**\n"
                    f"📁 File: `{filename}`\n"
                    f"🔸 Part: `{part_index}/{total_parts}`\n"
                    f"📦 Size: `{human_readable_size(part_size)}`\n"
                )
                
                mime, _ = mimetypes.guess_type(part)
                mime = mime or "application/octet-stream"
                
                try:
                    if mime.startswith("video"):
                        thumbnail = extract_thumbnail(part)
                        duration = get_duration(part)
                        await msg.reply_video(
                            video=part,
                            caption=f"🎬 {filename}",
                            supports_streaming=True,
                            duration=duration,
                            thumb=thumbnail,
                            progress=create_upload_callback(msg, part_name)
                        )
                        if thumbnail and os.path.exists(thumbnail):
                            os.remove(thumbnail)
                    elif mime.startswith("audio"):
                        duration = get_duration(part)
                        await msg.reply_audio(
                            audio=part,
                            caption=f"🎵 {filename}",
                            duration=duration,
                            progress=create_upload_callback(msg, part_name)
                        )
                    elif mime.startswith("image"):
                        await msg.reply_photo(
                            photo=part,
                            caption=f"🖼️ {filename}",
                            progress=create_upload_callback(msg, part_name)
                        )
                    else:
                        await msg.reply_document(
                            document=part,
                            caption=f"📦 {filename}",
                            progress=create_upload_callback(msg, part_name)
                        )
                except Exception as e:
                    op_logger.error(f"Upload failed for {part}: {str(e)}")
                    await safe_edit_message(msg, f"❌ Upload failed for {part_name}: {str(e)}")
                finally:
                    if os.path.exists(part):
                        os.remove(part)
        return True
    except Exception as e:
        op_logger.error(f"Processing error: {str(e)}")
        await safe_edit_message(msg, f"❌ Processing error: {str(e)}")
        return False
    finally:
        clean_directory(str(USER_DIR))

def create_upload_callback(msg, part_name):
    last_reported = 0
    
    async def callback(current, total):
        nonlocal last_reported
        if total <= 0:
            return
            
        percent = math.floor(current * 100 / total)
        current_time = time.time()
        
        if percent == 100 or current_time - last_reported > 5:
            text = (
                f"📤 **Uploading...**\n"
                f"📁 File: `{part_name}`\n"
                f"📊 Progress: {human_readable_size(current)} / {human_readable_size(total)}\n"
                f"🔸 {progress_bar(percent)} 🔸\n"
            )
            await safe_edit_message(msg, text)
            last_reported = current_time
    
    return callback

async def process_user_queue(user_id):
    while user_queues.get(user_id) and user_queues[user_id]:
        link = user_queues[user_id][0]
        try:
            msg = await bot.send_message(user_id, "🔄 Processing started...")
            user_messages[user_id] = msg
            active_downloads[user_id] = msg.id
            success = await process_torrent(user_id, link, msg)
            
            if not success:
                await safe_edit_message(msg, "❌ Processing failed")
            else:
                try:
                    await msg.delete()
                except:
                    pass
        except Exception as e:
            op_logger.error(f"Queue processing error: {str(e)}")
            if user_id in user_messages:
                await safe_edit_message(user_messages[user_id], f"❌ Error: {str(e)}")
        finally:
            if user_queues.get(user_id):
                user_queues[user_id].popleft()
                if not user_queues[user_id]:
                    del user_queues[user_id]
                    if user_id in user_messages:
                        del user_messages[user_id]
                    if user_id in active_downloads:
                        del active_downloads[user_id]
        await asyncio.sleep(1)
    if user_id in user_active_tasks:
        user_active_tasks[user_id] -= 1
        if user_active_tasks[user_id] <= 0:
            del user_active_tasks[user_id]

@bot.on_message((filters.private | filters.group) & (filters.text | filters.document))
async def message_handler(client: Client, message: Message):
    user_id = message.from_user.id
    text = message.text.strip() if message.text else None
    is_torrent_file = message.document and message.document.file_name.lower().endswith('.torrent')

    is_magnet = text and re.match(MAGNET_REGEX, text, re.IGNORECASE)
    is_torrent_url = text and re.match(TORRENT_REGEX, text, re.IGNORECASE)

    if not (is_magnet or is_torrent_url or is_torrent_file):
        await message.reply("❌ Please send a valid magnet link, .torrent URL, or .torrent file.")
        return

    # Check if user has an active task
    if user_id in user_active_tasks and user_active_tasks[user_id] > 0:
        await message.reply("❌ You already have an active task. Please wait until it completes.")
        return

    link = None
    if is_torrent_file:
        try:
            file_path = await message.download(os.path.join(DOWNLOAD_DIR, f"user_{user_id}_{int(time.time())}.torrent"))
            link = file_path
        except Exception as e:
            op_logger.error(f"Error downloading torrent file: {str(e)}")
            await message.reply(f"❌ Failed to download .torrent file: {str(e)}")
            return
    elif is_magnet or is_torrent_url:
        link = text

    if user_id not in user_queues:
        user_queues[user_id] = deque()
    if user_id not in user_active_tasks:
        user_active_tasks[user_id] = 0

    user_queues[user_id].append(link)
    user_active_tasks[user_id] += 1
    asyncio.create_task(process_user_queue(user_id))

async def cleanup_scheduler():
    while True:
        try:
            download_dir = Path(DOWNLOAD_DIR)
            if download_dir.exists():
                for entry in download_dir.iterdir():
                    if entry.is_dir() and time.time() - entry.stat().st_mtime > 3600:
                        clean_directory(str(entry))
        except Exception as e:
            op_logger.error(f"Cleanup error: {str(e)}")
        await asyncio.sleep(3600)

if __name__ == "__main__":
    try:
        op_logger.info("🚀 Starting Torrent Downloader Bot...")
        os.makedirs(DOWNLOAD_DIR, exist_ok=True)
        loop = asyncio.get_event_loop()
        loop.create_task(cleanup_scheduler())
        bot.run()
    except Exception as e:
        op_logger.error(f"Bot startup failed: {str(e)}")
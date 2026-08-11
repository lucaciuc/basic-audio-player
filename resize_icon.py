import os
from PIL import Image
import shutil

icon_path = r"C:\Users\Andrew\.gemini\antigravity-ide\brain\b7f81bd9-db85-4a18-b4f5-bf71c0c061d0\basic_audio_player_icon_1786491292997.png"
res_dir = r"C:\Users\Andrew\Desktop\android_testing_space\BasicAudioPlayer\app\src\main\res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Remove the XML adaptive icon to force fallback to static png
anydpi_dir = os.path.join(res_dir, "mipmap-anydpi-v26")
if os.path.exists(anydpi_dir):
    shutil.rmtree(anydpi_dir)

img = Image.open(icon_path).convert("RGBA")

for dpi, size in sizes.items():
    folder = os.path.join(res_dir, f"mipmap-{dpi}")
    os.makedirs(folder, exist_ok=True)
    
    # Generate standard icon
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(folder, "ic_launcher.png"))
    resized.save(os.path.join(folder, "ic_launcher_round.png"))
    
    # Delete old webp files to avoid conflicts
    for f in ["ic_launcher.webp", "ic_launcher_round.webp"]:
        old_file = os.path.join(folder, f)
        if os.path.exists(old_file):
            os.remove(old_file)

print("Icons successfully generated and old files cleaned up.")

import os
import shutil
from datetime import datetime
import subprocess

base_dir = '/home/lobster/Desktop/Projects/Owler/output'
timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
archive_dir = os.path.join(base_dir, 'archive', f'crawl_{timestamp}')

os.makedirs(archive_dir, exist_ok=True)

for root, dirs, files in os.walk(base_dir):
    if archive_dir in root:
        continue
    if root.startswith(archive_dir):
        continue

    for file in files:
        if any(file.endswith(ext) for ext in ['.txt', '.warc.gz', '.warc.gz.crc', '.json']):
            relative_path = os.path.relpath(root, base_dir)
            
            target_dir = os.path.join(archive_dir, relative_path)
            os.makedirs(target_dir, exist_ok=True)
            
            source_path = os.path.join(root, file)
            dest_path = os.path.join(target_dir, file)
            
            shutil.move(source_path, dest_path)
            print(f'Moved: {source_path} -> {dest_path}')

print("Archiving complete.")

print("Stopping and removing all Docker containers...")
subprocess.run("docker stop $(docker ps -q)", shell=True, check=True)
subprocess.run("docker rm $(docker ps -a -q)", shell=True, check=True)


print("Removing all Docker volumes...")
subprocess.run("docker volume prune -f", shell=True, check=True)


print("Archiving and Docker cleanup complete.")
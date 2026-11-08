import sys, subprocess, plistlib, os, shutil

def patch_dmg(dmg_path, icns_path):
    dmg_path = os.path.abspath(dmg_path)
    icns_path = os.path.abspath(icns_path)
    rw_dmg = dmg_path + ".rw.dmg"
    if os.path.exists(rw_dmg):
        os.remove(rw_dmg)
    
    print(f"Converting {dmg_path} to read-write...")
    subprocess.run(["hdiutil", "convert", dmg_path, "-format", "UDRW", "-o", rw_dmg], check=True)
    
    print("Attaching read-write image...")
    out = subprocess.run(["hdiutil", "attach", rw_dmg, "-readwrite", "-nobrowse", "-plist"], capture_output=True, check=True)
    plist_data = plistlib.loads(out.stdout)
    mount_point = None
    for entity in plist_data.get("system-entities", []):
        if "mount-point" in entity:
            mount_point = entity["mount-point"]
            break
            
    if not mount_point:
        raise RuntimeError("Could not find mount point for DMG")
        
    print(f"Mounted at {mount_point}")
    try:
        target_icon = os.path.join(mount_point, ".VolumeIcon.icns")
        if os.path.exists(target_icon):
            try:
                os.chmod(target_icon, 0o644)
            except Exception:
                pass
        shutil.copyfile(icns_path, target_icon)
        try:
            os.chmod(target_icon, 0o444)
        except Exception:
            pass
        
        subprocess.run(["SetFile", "-c", "icnC", target_icon], check=False)
        subprocess.run(["SetFile", "-a", "C", mount_point], check=False)
    finally:
        subprocess.run(["hdiutil", "detach", mount_point, "-force"], check=True)
        
    print("Converting back to compressed read-only DMG...")
    subprocess.run(["hdiutil", "convert", rw_dmg, "-format", "UDZO", "-o", dmg_path, "-ov"], check=True)
    if os.path.exists(rw_dmg):
        os.remove(rw_dmg)
        
    swift_cmd = f'''
import Cocoa
let ws = NSWorkspace.shared
let img = NSImage(contentsOfFile: "{icns_path}")
ws.setIcon(img, forFile: "{dmg_path}", options: [])
'''
    subprocess.run(["swift", "-e", swift_cmd], check=False)
    print("DMG volume icon and file icon successfully patched!")

if __name__ == "__main__":
    patch_dmg(sys.argv[1], sys.argv[2])

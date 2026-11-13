"""Check the visible controller or Probe result on an attached Android device."""
import argparse
import json
import math
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("screen", choices=["controller", "probe"])
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--latitude", type=float)
    parser.add_argument("--longitude", type=float)
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial, "shell"]
    path = f"/data/local/tmp/geopin-check-{time.time_ns()}.xml"
    subprocess.run(adb + ["uiautomator", "dump", path], check=True,
                   capture_output=True, timeout=20)
    result = subprocess.run(adb + ["cat", path], check=True,
                            capture_output=True, timeout=10)
    texts = [node.get("text", "") for node in ET.fromstring(result.stdout).iter("node")]
    if args.screen == "controller":
        assert "Modul aktif di LSPosed" in texts, "Controller does not report an active module"
        print(json.dumps({"controller_module_active": True}))
        return
    assert args.latitude is not None and args.longitude is not None, "Provide the test coordinate"
    readout = next((text for text in texts if "latitude   :" in text), "")
    assert readout, "Probe has no location reading"
    values = dict(re.findall(r"^([\w/]+)\s*:\s*(.+)$", readout, re.M))
    latitude = float(values["latitude"])
    longitude = float(values["longitude"])
    dy = math.radians(latitude - args.latitude)
    dx = math.radians(longitude - args.longitude) * math.cos(math.radians(args.latitude))
    distance = 6371000 * math.hypot(dx, dy)
    assert distance <= 1.1, f"Probe is {distance:.1f} metres from the test coordinate"
    accuracy = float(values["accuracy"].split()[0])
    assert 3.5 <= accuracy <= 7.8, f"Unexpected accuracy: {accuracy}"
    assert float(values["speed"].split()[0]) == 0, "Stationary speed is not zero"
    assert values["mock"] == "false", "Probe reports a mock location"
    assert values["hasAcc/Alt"] == "true/true", "Location flags are inconsistent"
    print(json.dumps({"probe_pin_verified": True, "distance_metres": round(distance, 3),
                      "accuracy_metres": accuracy, "mock": False}))


if __name__ == "__main__":
    main()

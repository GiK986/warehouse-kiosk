#!/bin/bash

adb install -r app/build/outputs/apk/release/warehouse-kiosk-release.apk

adb shell am start -n com.warehouse.kiosk/.MainActivity
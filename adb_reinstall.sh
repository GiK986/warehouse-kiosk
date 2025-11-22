#!/bin/bash

adb install -r app/build/outputs/apk/debug/warehouse-kiosk-release.apk

adb shell am start -n com.warehouse.kiosk/.MainActivity
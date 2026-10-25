#!/bin/sh
# Kembalikan semua paket Tier 1 + animasi 1x. Jalankan: adb -s 192.168.18.136:5555 shell < debloat/restore_tier1.sh
pm enable com.android.dreams.basic
pm enable com.android.dreams.phototable
pm enable com.android.printspooler
pm enable com.android.bips
pm enable com.android.printservice.recommendation
pm enable com.android.bookmarkprovider
pm enable com.android.providers.partnerbookmarks
pm enable com.android.emergency
pm enable com.android.traceur
pm enable com.android.calllogbackup
pm enable com.android.wallpaperbackup
pm enable com.android.dynsystem
pm enable com.android.bluetoothmidiservice
pm enable com.android.hotspot2
pm enable com.sprd.logmanager
pm enable com.sprd.autoslt
pm enable com.sprd.validationtools
pm enable com.sprd.cameracalibration
pm enable com.spreadtrum.sgps
pm enable com.sprd.omacp
pm enable com.tw.bug.report
pm enable com.google.android.gmsintegration
settings put global window_animation_scale 1.0
settings put global transition_animation_scale 0.5
settings put global animator_duration_scale 1.0

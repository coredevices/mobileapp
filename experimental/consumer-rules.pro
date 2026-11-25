# Glance instantiates ActionCallbacks reflectively by class name
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

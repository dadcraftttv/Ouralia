# Android build of the dcs library (tools/build_dcs_android.sh): libdcs.so, which Pd on Android finds for
# [declare -lib dcs] in the app's native library folder. It links against the libpd.so in our pd-core.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := pd
LOCAL_SRC_FILES := $(PDCORE_JNI)/$(TARGET_ARCH_ABI)/libpd.so
include $(PREBUILT_SHARED_LIBRARY)

# the effect ports (they include dcs_fx.h) match Pd's objects only with exact float arithmetic: no -ffast-math
DCS_ALL := $(patsubst $(LOCAL_PATH)/%,%,$(wildcard $(LOCAL_PATH)/../src/*.c))
DCS_FX := $(patsubst $(LOCAL_PATH)/%,%,$(shell grep -l '"dcs_fx.h"' $(LOCAL_PATH)/../src/*.c))

include $(CLEAR_VARS)
LOCAL_MODULE := dcsfx
LOCAL_C_INCLUDES := $(PD_SRC)
LOCAL_CFLAGS := -DPD -O3 -fno-fast-math -Wall -Wno-unused-parameter
LOCAL_SRC_FILES := $(DCS_FX)
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := dcs
LOCAL_C_INCLUDES := $(PD_SRC)
LOCAL_CFLAGS := -DPD -O3 -ffast-math -Wall -Wno-unused-parameter
LOCAL_SRC_FILES := $(filter-out $(DCS_FX),$(DCS_ALL))
LOCAL_WHOLE_STATIC_LIBRARIES := dcsfx
LOCAL_SHARED_LIBRARIES := pd
LOCAL_LDLIBS := -lm
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)

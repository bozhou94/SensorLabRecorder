LOCAL_PATH:=$(call my-dir)

include $(CLEAR_VARS)
LOCAL_CFLAGS:= -Wall -g -std=c99
LOCAL_CPPFLAGS:= -Wall -g
LOCAL_MODULE:= mltoolkit 
LOCAL_SRC_FILES:=procAcc.c buffer.c MLToolKit.c mvnpdf.cpp jni_mltoolkit.c my_mean.c features.c kiss_fft.c kiss_fftr.c mfcc.cpp procAudio.cpp classifier.c
LOCAL_LDLIBS := -lm -lc -llog 
#LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib 
include $(BUILD_SHARED_LIBRARY)

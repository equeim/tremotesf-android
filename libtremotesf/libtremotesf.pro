TARGET = tremotesf
TEMPLATE = lib

CONFIG += c++1z

QMAKE_CXXFLAGS += -Wall -Wextra -pedantic
DEFINES += QT_DEPRECATED_WARNINGS QT_DISABLE_DEPRECATED_BEFORE=0x050800

QT = core network concurrent

HEADERS = rpc.h serversettings.h serverstats.h torrent.h tracker.h jni/jnirpc.h
SOURCES = rpc.cpp serversettings.cpp serverstats.cpp torrent.cpp tracker.cpp jni/jnirpc.cpp jni/libtremotesf_wrap.cxx

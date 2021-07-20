%module(directors="1") libtremotesf
%{
#include "libtremotesf/rpc.h"
#include "libtremotesf/torrent.h"
#include "libtremotesf/tracker.h"
#include "libtremotesf/serverstats.h"
#include "libtremotesf/serversettings.h"
#include "jnirpc.h"
%}

%include "enums.swg"
%javaconst(1);

%include <std_vector.i>
%include <std_unordered_map.i>

%include "qbytearray.i"
%include "qstring.i"
%include "qvariantlist.i"
%include "qtime.i"

%ignore QT_VERSION_MAJOR;
%ignore QT_VERSION_MINOR;
%ignore QT_VERSION_PATCH;
%ignore QT_VERSION;

#define QT_VERSION_CHECK(major, minor, patch) ((major<<16)|(minor<<8)|(patch))
// Try to keep it in sync with qtbase submodule
#define QT_VERSION_MAJOR 6
#define QT_VERSION_MINOR 1
#define QT_VERSION_PATCH 2
#define QT_VERSION QT_VERSION_CHECK(QT_VERSION_MAJOR, QT_VERSION_MINOR, QT_VERSION_PATCH)
#define Q_NAMESPACE
#define Q_OBJECT
#define Q_GADGET
#define Q_ENUM(T)
#define Q_ENUM_NS(T)
#define Q_PROPERTY(T)
#define Q_INVOKABLE
#define signals private

%immutable;

namespace libtremotesf
{
    %ignore Rpc;
    %typemap(javafinalize) JniRpc ""

    %ignore Torrent;
    %ignore TorrentData::update;
    %ignore TorrentData::addedDate;
    %ignore TorrentData::activityDate;
    %ignore TorrentData::doneDate;
    %ignore TorrentData::creationDate;

    %ignore TorrentFile::TorrentFile;
    %ignore TorrentFile::update;
    %ignore Peer::Peer;
    %ignore Peer::update;
    %ignore Peer::addressKey;

    %ignore Tracker::Tracker;
    %ignore Tracker::UpdateResult;
    %ignore Tracker::update;

    %ignore ServerStats;
    %ignore SessionStats::update;

    %ignore ServerSettings;
    %ignore JniServerSettingsData::JniServerSettingsData(ServerSettings*);
    %ignore Rpc::serverSettings;
    %typemap(javafinalize) ServerSettingsData ""
    %typemap(javafinalize) JniServerSettingsData ""
}

%typemap(javafinalize) std::vector<libtremotesf::TorrentData*> ""
%newobject std::vector<libtremotesf::TorrentData*>::doGet;
%template(TorrentDataVector) std::vector<libtremotesf::TorrentData*>;

%template(TrackersVector) std::vector<libtremotesf::Tracker>;

%typemap(javafinalize) std::vector<libtremotesf::TorrentFile*> ""
%newobject std::vector<libtremotesf::TorrentFile*>::doGet;
%template(TorrentFilesVector) std::vector<libtremotesf::TorrentFile*>;

%typemap(javafinalize) std::vector<libtremotesf::Peer*> ""
%newobject std::vector<libtremotesf::Peer*>::doGet;
%template(TorrentPeersVector) std::vector<libtremotesf::Peer*>;

%typemap(javafinalize) std::vector<QString> ""
%template(StringsVector) std::vector<QString>;
%typemap(javafinalize) std::unordered_map<QString, QString> ""
%template(StringMap) std::unordered_map<QString, QString>;

%typemap(javafinalize) std::vector<int> ""
%template(IntVector) std::vector<int>;

%include "libtremotesf/peer.h"
%include "libtremotesf/torrentfile.h"
%include "libtremotesf/tracker.h"
%include "libtremotesf/torrent.h"

%mutable;
%include "libtremotesf/qtsupport.h"
%include "libtremotesf/rpc.h"
%immutable;

%include "libtremotesf/serversettings.h"
%include "libtremotesf/serverstats.h"

%feature("director") libtremotesf::JniRpc;
%include "jnirpc.h"

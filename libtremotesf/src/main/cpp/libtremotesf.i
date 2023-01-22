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

%include <std_map.i>
%include <std_pair.i>
%include <std_vector.i>

%include "qbytearray.i"
%include "qdatetime.i"
%include "qstring.i"
%include "qtime.i"

%ignore QT_VERSION_MAJOR;
%ignore QT_VERSION_MINOR;
%ignore QT_VERSION_PATCH;
%ignore QT_VERSION;

#define QT_VERSION_CHECK(major, minor, patch) ((major<<16)|(minor<<8)|(patch))
// Try to keep it in sync with qtbase submodule
#define QT_VERSION_MAJOR 6
#define QT_VERSION_MINOR 4
#define QT_VERSION_PATCH 1
#define QT_VERSION QT_VERSION_CHECK(QT_VERSION_MAJOR, QT_VERSION_MINOR, QT_VERSION_PATCH)
#define Q_NAMESPACE
#define Q_OBJECT
#define Q_GADGET
#define Q_ENUM(T)
#define Q_ENUM_NS(T)
#define Q_PROPERTY(T)
#define Q_INVOKABLE
#define signals private
using qint64 = long long;

#define SPECIALIZE_FORMATTER_FOR_Q_ENUM(T)
#define FORMAT_CONST
namespace fmt {}
%ignore fmt::formatter;
%ignore libtremotesf::SimpleFormatter;

%immutable;

namespace libtremotesf
{
    %ignore Rpc;

    %ignore Torrent;
    %ignore TorrentData::TorrentData;
    %ignore TorrentData::priorityToInt;
    %ignore TorrentData::update;

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
    %ignore ServerSettingsData::ServerSettingsData;
    %ignore JniServerSettingsData::JniServerSettingsData(ServerSettings*);
    %ignore Rpc::serverSettings;

    %rename(swigEquals) ConnectionConfiguration::operator==;
}

%typemap(javacode) libtremotesf::ConnectionConfiguration %{
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof $javaclassname)) return false;
    final $javaclassname otherCasted = ($javaclassname) other;
    if (otherCasted.swigCPtr == swigCPtr) return true;
    if (otherCasted.swigCPtr != 0 && swigCPtr != 0) return swigEquals(otherCasted);
    return false;
  }
%}

%template(TorrentDataVector) std::vector<libtremotesf::TorrentData>;
%template(TrackersVector) std::vector<libtremotesf::Tracker>;
%template(TorrentFilesVector) std::vector<libtremotesf::TorrentFile>;
%template(TorrentPeersVector) std::vector<libtremotesf::Peer>;
%template(StringsVector) std::vector<QString>;
%template(StringMap) std::map<QString, QString>;
%template(IntPair) std::pair<int, int>;
%template(IntVector) std::vector<int>;
%template(IntPairVector) std::vector<std::pair<int, int>>;

%include "libtremotesf/peer.h"
%include "libtremotesf/torrentfile.h"
%include "libtremotesf/tracker.h"
%include "libtremotesf/torrent.h"

%mutable;
%include "libtremotesf/rpc.h"
%immutable;

%include "libtremotesf/serversettings.h"
%include "libtremotesf/serverstats.h"

%feature("director") libtremotesf::JniRpc;
%include "jnirpc.h"

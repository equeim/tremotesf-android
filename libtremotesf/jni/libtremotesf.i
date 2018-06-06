%module(directors="1") libtremotesf
%{
#include "../rpc.h"
#include "../torrent.h"
#include "../tracker.h"
#include "../serverstats.h"
#include "../serversettings.h"
#include "jnirpc.h"
%}

%include "enumtypeunsafe.swg"
%javaconst(1);

%include <std_vector.i>
%include <std_shared_ptr.i>

%include "qbytearray.i"
%include "qstring.i"
%include "qvariantlist.i"
%include "qdatetime.i"
%include "qtime.i"

#define Q_OBJECT
#define Q_ENUMS(T)
#define Q_ENUM(T)
#define Q_PROPERTY(T)
#define Q_INVOKABLE
#define signals private

namespace libtremotesf
{
    %ignore Server;
    
    %ignore Rpc::Rpc;
    %ignore Rpc::setServer(const libtremotesf::Server& server);
    %ignore Rpc::setSessionProperty;
    %ignore Rpc::setSessionProperties;
    %ignore Rpc::setTorrentProperty;
    %rename("BaseRpc") Rpc;

    %ignore Torrent::Torrent;
    %ignore Torrent::setDownloadSpeedLimited;
    %ignore Torrent::setDownloadSpeedLimit;
    %ignore Torrent::setUploadSpeedLimited;
    %ignore Torrent::setUploadSpeedLimit;
    %ignore Torrent::setRatioLimitMode;
    %ignore Torrent::setRatioLimit;
    %ignore Torrent::setPeersLimit;
    %ignore Torrent::setHonorSessionLimits;
    %ignore Torrent::setBandwidthPriority;
    %ignore Torrent::setIdleSeedingLimitMode;
    %ignore Torrent::setIdleSeedingLimit;
    %ignore Torrent::setFilesEnabled;
    %ignore Torrent::isFilesUpdated;
    %ignore Torrent::setFilesWanted;
    %ignore Torrent::setFilesPriority;
    %ignore Torrent::renameFile;
    %ignore Torrent::addTracker;
    %ignore Torrent::setTracker;
    %ignore Torrent::removeTrackers;
    %ignore Torrent::setPeersEnabled;
    %ignore Torrent::isPeersUpdated;
    %ignore Torrent::isUpdated;
    %ignore Torrent::update;
    %ignore Torrent::updateFiles;
    %ignore Torrent::updatePeers;
    %ignore TorrentFile::TorrentFile;
    %ignore Peer::Peer;
    %ignore Peer::update;

    %ignore Tracker::Tracker;
    %ignore Tracker::update;

    %ignore ServerStats::ServerStats;
    %ignore ServerStats::update;
    %ignore SessionStats::SessionStats;
    %ignore SessionStats::update;
    %immutable SessionStats::downloaded;
    %immutable SessionStats::uploaded;
    %immutable SessionStats::duration;
    %immutable SessionStats::sessionCount;
    
    %ignore ServerSettings::ServerSettings;
    %ignore ServerSettings::toKibiBytes;
    %ignore ServerSettings::fromKibiBytes;
    %ignore ServerSettings::save;
    %ignore ServerSettings::update;
    
    %ignore JniServerSettings::JniServerSettings;
}

%shared_ptr(libtremotesf::Torrent)

%ignore std::vector<std::shared_ptr<libtremotesf::Torrent>>::vector;
%template(TorrentsVector) std::vector<std::shared_ptr<libtremotesf::Torrent>>;

%shared_ptr(libtremotesf::TorrentFile)
%ignore std::vector<std::shared_ptr<libtremotesf::TorrentFile>>::vector;
%template(TorrentFilesVector) std::vector<std::shared_ptr<libtremotesf::TorrentFile>>;

%shared_ptr(libtremotesf::Tracker)
%ignore std::vector<std::shared_ptr<libtremotesf::Tracker>>::vector;
%template(TrackersVector) std::vector<std::shared_ptr<libtremotesf::Tracker>>;

%shared_ptr(libtremotesf::Peer)
%ignore std::vector<std::shared_ptr<libtremotesf::Peer>>::vector;
%template(TorrentPeersVector) std::vector<std::shared_ptr<libtremotesf::Peer>>;

%ignore std::vector<QString>::vector;
%template(StringsVector) std::vector<QString>;

%include "../torrent.h"
%include "../tracker.h"
%include "../rpc.h"
%include "../serversettings.h"
%include "../serverstats.h"

%feature("director") libtremotesf::JniRpc;
%include "jnirpc.h"


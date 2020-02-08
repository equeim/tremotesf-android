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
#define Q_GADGET
#define Q_ENUM(T)
#define Q_PROPERTY(T)
#define Q_INVOKABLE
#define signals private

%immutable;

namespace libtremotesf
{
    %ignore Server;
    %rename($ignore, regextarget=1, fullname=1, %$not %$isenum, %$not %$isenumitem, notmatch$name="serverStats") "Rpc::.*$";

    %rename($ignore, regextarget=1, fullname=1) "Torrent::set.*$";
    %ignore Torrent::Torrent;
    %ignore Torrent::idKey;
    %ignore Torrent::isFilesEnabled;
    %ignore Torrent::isFilesUpdated;
    %ignore Torrent::renameFile;
    %ignore Torrent::addTracker;
    %ignore Torrent::setTracker;
    %ignore Torrent::removeTrackers;
    %ignore Torrent::isPeersEnabled;
    %ignore Torrent::isPeersUpdated;
    %ignore Torrent::isUpdated;
    %ignore Torrent::update;
    %ignore Torrent::updateFiles;
    %ignore Torrent::updatePeers;
    %ignore Torrent::doneDate;
    %ignore Torrent::isSingleFile;
    %ignore Torrent::queuePosition;

    %ignore TorrentData::update;

    %ignore TorrentFile::TorrentFile;
    %ignore TorrentFile::update;
    %ignore Peer::Peer;
    %ignore Peer::update;
    %ignore Peer::addressKey;

    %ignore Tracker::Tracker;
    %ignore Tracker::update;

    %ignore ServerStats::ServerStats;
    %ignore ServerStats::update;
    %ignore SessionStats::SessionStats;
    %ignore SessionStats::update;
    %ignore ServerSettings::ServerSettings;
    %ignore ServerSettings::toKibiBytes;
    %ignore ServerSettings::fromKibiBytes;
    %ignore ServerSettings::save;
    %ignore ServerSettings::update;
    %ignore ServerSettings::minimumRpcVersion;
    %ignore ServerSettings::rpcVersion;
    %ignore ServerSettings::saveOnSet;
    %ignore ServerSettings::trashTorrentFiles;
    %rename($ignore, regextarget=1, fullname=1, %$not %$isenum, %$not %$isenumitem) "ServerSettings::set.*$";
    %ignore JniServerSettings::JniServerSettings;
}

%shared_ptr(libtremotesf::Torrent)
%template(TorrentsVector) std::vector<std::shared_ptr<libtremotesf::Torrent>>;
%template(TorrentDataVector) std::vector<libtremotesf::TorrentData>;

%template(TorrentFilesVector) std::vector<libtremotesf::TorrentFile>;
%template(TrackersVector) std::vector<libtremotesf::Tracker>;
%template(TorrentPeersVector) std::vector<libtremotesf::Peer>;

%template(StringsVector) std::vector<QString>;

%template(IntVector) std::vector<int>;

%include "../peer.h"
%include "../torrentfile.h"
%include "../tracker.h"
%include "../torrent.h"
%include "../rpc.h"
%include "../serversettings.h"
%include "../serverstats.h"

%feature("director") libtremotesf::JniRpc;
%include "jnirpc.h"

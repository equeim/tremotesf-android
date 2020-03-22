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
%include <std_unordered_map.i>

%include "qbytearray.i"
%include "qstring.i"
%include "qvariantlist.i"
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
    %rename($ignore, regextarget=1, fullname=1, %$not %$isenum, %$not %$isenumitem) "Rpc::.*$";
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
    %ignore Tracker::update;

    %ignore ServerStats;
    %ignore SessionStats::update;

    %ignore ServerSettings;
    %ignore JniServerSettingsData::JniServerSettingsData;
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

%template(StringsVector) std::vector<QString>;
%template(StringMap) std::unordered_map<QString, QString>;

%template(IntVector) std::vector<int>;

%include "../peer.h"
%include "../torrentfile.h"
%include "../tracker.h"
%include "../torrent.h"

%mutable;
%include "../rpc.h"
%immutable;

%include "../serversettings.h"
%include "../serverstats.h"

%feature("director") libtremotesf::JniRpc;
%include "jnirpc.h"

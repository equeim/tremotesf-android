#include "jnirpc.h"

#include <array>
#include <string>

#include <QThread>
#include <QCoreApplication>

#include <jni.h>

#include "rpc.h"
#include "serverstats.h"

Q_DECLARE_METATYPE(libtremotesf::Server)
Q_DECLARE_METATYPE(libtremotesf::TorrentFile::Priority)

namespace libtremotesf
{
    namespace
    {
        template<typename I, typename O, typename IndexIterator, typename Functor>
        std::vector<O*> toNewPointers(const std::vector<I>& items, IndexIterator&& begin, IndexIterator&& end, Functor&& transform)
        {
            std::vector<O*> v;
            v.reserve(end - begin);
            for (auto i = begin; i != end; ++i) {
                v.push_back(new O(transform(items[static_cast<size_t>(*i)])));
            }
            return v;
        }

        template<typename I, typename IndexIterator>
        std::vector<I*> toNewPointers(const std::vector<I>& items, IndexIterator&& begin, IndexIterator&& end)
        {
            return toNewPointers<I, I>(items, begin, end, [](const I& i) -> const I& { return i; });
        }

        template<typename IndexIterator>
        std::vector<TorrentData*> toNewPointers(const std::vector<std::shared_ptr<Torrent>>& items, IndexIterator&& begin, IndexIterator&& end)
        {
            return toNewPointers<std::shared_ptr<Torrent>, TorrentData>(items, begin, end, [](const std::shared_ptr<Torrent>& i) { return i->data(); });
        }

        struct IndexIterator
        {
            size_t value;
            operator size_t() { return value; }
            size_t operator*() { return value; }
            IndexIterator& operator++() { ++value; return *this; }
        };

        template<typename Functor>
        void runOnTorrent(Rpc* rpc, const TorrentData& data, Functor function)
        {
            QMetaObject::invokeMethod(rpc, [rpc, id = data.id, function = std::move(function)] {
                const auto torrent = rpc->torrentById(id);
                if (torrent) {
                    function(torrent);
                }
            });
        }
    }

    JniServerSettingsData::JniServerSettingsData(ServerSettings* settings)
        : ServerSettingsData(settings->data()),
          mSettings(settings)
    {
        qRegisterMetaType<ServerSettingsData::AlternativeSpeedLimitsDays>();
        qRegisterMetaType<ServerSettingsData::EncryptionMode>();
    }

    void JniServerSettingsData::setDownloadDirectory(const QString& directory)
    {
        downloadDirectory = directory;
        QMetaObject::invokeMethod(mSettings, "setDownloadDirectory", Q_ARG(QString, directory));
    }

    void JniServerSettingsData::setStartAddedTorrents(bool start)
    {
        startAddedTorrents = start;
        QMetaObject::invokeMethod(mSettings, "setStartAddedTorrents", Q_ARG(bool, start));
    }

    void JniServerSettingsData::setTrashTorrentFiles(bool trash)
    {
        trashTorrentFiles = trash;
        QMetaObject::invokeMethod(mSettings, "setTrashTorrentFiles", Q_ARG(bool, trash));
    }

    void JniServerSettingsData::setRenameIncompleteFiles(bool rename)
    {
        renameIncompleteFiles = rename;
        QMetaObject::invokeMethod(mSettings, "setRenameIncompleteFiles", Q_ARG(bool, rename));
    }

    void JniServerSettingsData::setIncompleteDirectoryEnabled(bool enabled)
    {
        incompleteDirectoryEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setIncompleteDirectoryEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setIncompleteDirectory(const QString& directory)
    {
        incompleteDirectory = directory;
        QMetaObject::invokeMethod(mSettings, "setIncompleteDirectory", Q_ARG(QString, directory));
    }

    void JniServerSettingsData::setRatioLimited(bool limited)
    {
        ratioLimited = limited;
        QMetaObject::invokeMethod(mSettings, "setRatioLimited", Q_ARG(bool, limited));
    }

    void JniServerSettingsData::setRatioLimit(double limit)
    {
        ratioLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setRatioLimit", Q_ARG(double, limit));
    }

    void JniServerSettingsData::setIdleSeedingLimited(bool limited)
    {
        idleQueueLimited = limited;
        QMetaObject::invokeMethod(mSettings, "setIdleSeedingLimited", Q_ARG(bool, limited));
    }

    void JniServerSettingsData::setIdleSeedingLimit(int limit)
    {
        idleSeedingLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setIdleSeedingLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setDownloadQueueEnabled(bool enabled)
    {
        downloadQueueEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setDownloadQueueEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setDownloadQueueSize(int size)
    {
        downloadQueueSize = size;
        QMetaObject::invokeMethod(mSettings, "setDownloadQueueSize", Q_ARG(int, size));
    }

    void JniServerSettingsData::setSeedQueueEnabled(bool enabled)
    {
        seedQueueEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setSeedQueueEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setSeedQueueSize(int size)
    {
        seedQueueSize = size;
        QMetaObject::invokeMethod(mSettings, "setSeedQueueSize", Q_ARG(int, size));
    }

    void JniServerSettingsData::setIdleQueueLimited(bool limited)
    {
        idleQueueLimited = limited;
        QMetaObject::invokeMethod(mSettings, "setIdleQueueLimited", Q_ARG(bool, limited));
    }

    void JniServerSettingsData::setIdleQueueLimit(int limit)
    {
        idleQueueLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setIdleQueueLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setDownloadSpeedLimited(bool limited)
    {
        downloadSpeedLimited = limited;
        QMetaObject::invokeMethod(mSettings, "setDownloadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniServerSettingsData::setDownloadSpeedLimit(int limit)
    {
        downloadSpeedLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setDownloadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setUploadSpeedLimited(bool limited)
    {
        uploadSpeedLimited = limited;
        QMetaObject::invokeMethod(mSettings, "setUploadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniServerSettingsData::setUploadSpeedLimit(int limit)
    {
        uploadSpeedLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setUploadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsEnabled(bool enabled)
    {
        alternativeSpeedLimitsEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setAlternativeSpeedLimitsEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setAlternativeDownloadSpeedLimit(int limit)
    {
        alternativeDownloadSpeedLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setAlternativeDownloadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setAlternativeUploadSpeedLimit(int limit)
    {
        alternativeUploadSpeedLimit = limit;
        QMetaObject::invokeMethod(mSettings, "setAlternativeUploadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsScheduled(bool scheduled)
    {
        alternativeSpeedLimitsScheduled = scheduled;
        QMetaObject::invokeMethod(mSettings, "setAlternativeSpeedLimitsScheduled", Q_ARG(bool, scheduled));
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsBeginTime(QTime time)
    {
        alternativeSpeedLimitsBeginTime = time;
        QMetaObject::invokeMethod(mSettings, "setAlternativeSpeedLimitsBeginTime", Q_ARG(QTime, time));
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsEndTime(QTime time)
    {
        alternativeSpeedLimitsEndTime = time;
        QMetaObject::invokeMethod(mSettings, "setAlternativeSpeedLimitsEndTime", Q_ARG(QTime, time));
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsDays(ServerSettingsData::AlternativeSpeedLimitsDays days)
    {
        alternativeSpeedLimitsDays = days;
        QMetaObject::invokeMethod(mSettings, "setAlternativeSpeedLimitsDays", Q_ARG(libtremotesf::ServerSettingsData::AlternativeSpeedLimitsDays, days));
    }

    void JniServerSettingsData::setPeerPort(int port)
    {
        peerPort = port;
        QMetaObject::invokeMethod(mSettings, "setPeerPort", Q_ARG(int, port));
    }

    void JniServerSettingsData::setRandomPortEnabled(bool enabled)
    {
        randomPortEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setRandomPortEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setPortForwardingEnabled(bool enabled)
    {
        portForwardingEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setPortForwardingEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setEncryptionMode(ServerSettingsData::EncryptionMode mode)
    {
        encryptionMode = mode;
        QMetaObject::invokeMethod(mSettings, "setEncryptionMode", Q_ARG(libtremotesf::ServerSettingsData::EncryptionMode, mode));
    }

    void JniServerSettingsData::setUtpEnabled(bool enabled)
    {
        utpEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setUtpEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setPexEnabled(bool enabled)
    {
        pexEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setPexEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setDhtEnabled(bool enabled)
    {
        dhtEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setDhtEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setLpdEnabled(bool enabled)
    {
        lpdEnabled = enabled;
        QMetaObject::invokeMethod(mSettings, "setLpdEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettingsData::setMaximumPeersPerTorrent(int peers)
    {
        maximumPeersPerTorrent = peers;
        QMetaObject::invokeMethod(mSettings, "setMaximumPeersPerTorrent", Q_ARG(int, peers));
    }

    void JniServerSettingsData::setMaximumPeersGlobally(int peers)
    {
        maximumPeersGlobally = peers;
        QMetaObject::invokeMethod(mSettings, "setMaximumPeersGlobally", Q_ARG(int, peers));
    }

    JniRpc::JniRpc()
    {
        qRegisterMetaType<Server>();
        qRegisterMetaType<Torrent::Priority>();
        qRegisterMetaType<Torrent::RatioLimitMode>();
        qRegisterMetaType<Torrent::IdleSeedingLimitMode>();
        qRegisterMetaType<TorrentFile::Priority>();

        mRpc.setUpdateDisabled(true);

        QObject::connect(&mRpc, &Rpc::aboutToDisconnect, [=]() { onAboutToDisconnect(); });
        QObject::connect(&mRpc, &Rpc::statusChanged, [=]() { onStatusChanged(mRpc.status()); });
        QObject::connect(&mRpc, &Rpc::errorChanged, [=]() { onErrorChanged(mRpc.error(), mRpc.errorMessage()); });

        QObject::connect(mRpc.serverSettings(), &ServerSettings::changed, &mRpc, [this] {
            onServerSettingsChanged(JniServerSettingsData(mRpc.serverSettings()));
        });

        QObject::connect(&mRpc, &Rpc::torrentsUpdated, [=](const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& t = mRpc.torrents();
            onTorrentsUpdated(removed,
                              toNewPointers(t, changed.begin(), changed.end()),
                              toNewPointers(t, IndexIterator{t.size() - added}, IndexIterator{t.size()}));
        });

        QObject::connect(&mRpc, &Rpc::torrentFilesUpdated, [=](const Torrent* torrent, const std::vector<int>& changed) {
            onTorrentFilesUpdated(torrent->id(), toNewPointers(torrent->files(), changed.begin(), changed.end()));
        });
        QObject::connect(&mRpc, &Rpc::torrentPeersUpdated, [=](const Torrent* torrent, const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& p = torrent->peers();
            onTorrentPeersUpdated(torrent->id(),
                                  removed,
                                  toNewPointers(p, changed.begin(), changed.end()),
                                  toNewPointers(p, IndexIterator{p.size() - added}, IndexIterator{p.size()}));
        });

        QObject::connect(&mRpc, &Rpc::torrentFileRenamed, [=](int torrentId, const QString& filePath, const QString& newName) {
            onTorrentFileRenamed(torrentId, filePath, newName);
        });

        QObject::connect(&mRpc, &Rpc::torrentAdded, [=](const Torrent* torrent) {
            onTorrentAdded(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(&mRpc, &Rpc::torrentFinished, [=](const Torrent* torrent) {
            onTorrentFinished(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(&mRpc, &Rpc::torrentAddDuplicate, [=]() { onTorrentAddDuplicate(); });
        QObject::connect(&mRpc, &Rpc::torrentAddError, [=]() { onTorrentAddError(); });

        QObject::connect(&mRpc, &Rpc::gotDownloadDirFreeSpace, [=](long long bytes) { onGotDownloadDirFreeSpace(bytes); });
        QObject::connect(&mRpc, &Rpc::gotFreeSpaceForPath, [=](const QString& path, bool success, long long bytes) { onGotFreeSpaceForPath(path, success, bytes); });

        const auto stats = mRpc.serverStats();
        QObject::connect(stats, &ServerStats::updated, [=] {
            onServerStatsUpdated(stats->downloadSpeed(), stats->uploadSpeed(), stats->currentSession(), stats->total());
        });

        auto thread = new QThread();
        thread->start();
        mRpc.moveToThread(thread);
    }

    JniServerSettingsData JniRpc::serverSettingsData() const
    {
        return JniServerSettingsData(mRpc.serverSettings());
    }

    void JniRpc::setServer(const Server& server)
    {
        QMetaObject::invokeMethod(&mRpc, "setServer", Q_ARG(libtremotesf::Server, server));
    }

    void JniRpc::resetServer()
    {
        QMetaObject::invokeMethod(&mRpc, "resetServer");
    }

    void JniRpc::connect()
    {
        QMetaObject::invokeMethod(&mRpc, "connect");
    }

    void JniRpc::disconnect()
    {
        QMetaObject::invokeMethod(&mRpc, "disconnect");
    }

    void JniRpc::setUpdateDisabled(bool disabled)
    {
        QMetaObject::invokeMethod(&mRpc, "setUpdateDisabled", Q_ARG(bool, disabled));
    }

    void JniRpc::addTorrentFile(const QByteArray& fileData, const QString& downloadDirectory, const QVariantList& unwantedFiles, const QVariantList& highPriorityFiles, const QVariantList& lowPriorityFiles, const std::unordered_map<QString, QString>& renamedFiles, int bandwidthPriority, bool start)
    {
        QVariantMap renamed;
        for (const auto& i : renamedFiles) {
            renamed.insert(i.first, i.second);
        }
        QMetaObject::invokeMethod(&mRpc,
                                  "addTorrentFile",
                                  Q_ARG(QByteArray, fileData),
                                  Q_ARG(QString, downloadDirectory),
                                  Q_ARG(QVariantList, unwantedFiles),
                                  Q_ARG(QVariantList, highPriorityFiles),
                                  Q_ARG(QVariantList, lowPriorityFiles),
                                  Q_ARG(QVariantMap, renamed),
                                  Q_ARG(int, bandwidthPriority),
                                  Q_ARG(bool, start));
    }

    void JniRpc::addTorrentLink(const QString& link, const QString& downloadDirectory, int bandwidthPriority, bool start)
    {
        QMetaObject::invokeMethod(&mRpc,
                                  "addTorrentLink",
                                  Q_ARG(QString, link),
                                  Q_ARG(QString, downloadDirectory),
                                  Q_ARG(int, bandwidthPriority),
                                  Q_ARG(bool, start));
    }

    void JniRpc::startTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "startTorrents", Q_ARG(QVariantList, ids));
    }

    /*void JniRpc::startTorrentsNow(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "startTorrentsNow", Q_ARG(QVariantList, ids));
    }*/

    void JniRpc::pauseTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "pauseTorrents", Q_ARG(QVariantList, ids));
    }

    void JniRpc::removeTorrents(const QVariantList& ids, bool deleteFiles)
    {
        QMetaObject::invokeMethod(&mRpc, "removeTorrents", Q_ARG(QVariantList, ids), Q_ARG(bool, deleteFiles));
    }

    void JniRpc::checkTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "checkTorrents", Q_ARG(QVariantList, ids));
    }

    /*void JniRpc::moveTorrentsToTop(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "moveTorrentsToTop", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsUp(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "moveTorrentsUp", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsDown(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "moveTorrentsDown", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsToBottom(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "moveTorrentsToBottom", Q_ARG(QVariantList, ids));
    }*/

    void JniRpc::reannounceTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(&mRpc, "reannounceTorrents", Q_ARG(QVariantList, ids));
    }

    void JniRpc::setTorrentsLocation(const QVariantList& ids, const QString& location, bool moveFiles)
    {
        QMetaObject::invokeMethod(&mRpc, "setTorrentsLocation", Q_ARG(QVariantList, ids), Q_ARG(QString, location), Q_ARG(bool, moveFiles));
    }

    void JniRpc::renameTorrentFile(int torrentId, const QString& filePath, const QString& newName)
    {
        QMetaObject::invokeMethod(&mRpc, "renameTorrentFile", Q_ARG(int, torrentId), Q_ARG(QString, filePath), Q_ARG(QString, newName));
    }

    void JniRpc::getDownloadDirFreeSpace()
    {
        QMetaObject::invokeMethod(&mRpc, "getDownloadDirFreeSpace");
    }

    void JniRpc::getFreeSpaceForPath(const QString& path)
    {
        QMetaObject::invokeMethod(&mRpc, "getFreeSpaceForPath", Q_ARG(QString, path));
    }

    void JniRpc::setTorrentDownloadSpeedLimited(TorrentData& data, bool limited)
    {
        data.downloadSpeedLimited = limited;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentDownloadSpeedLimit(TorrentData& data, int limit)
    {
        data.downloadSpeedLimit = limit;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimited(TorrentData& data, bool limited)
    {
        data.uploadSpeedLimited = limited;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimit(TorrentData& data, int limit)
    {
        data.uploadSpeedLimit = limit;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentRatioLimitMode(TorrentData& data, Torrent::RatioLimitMode mode)
    {
        data.ratioLimitMode = mode;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setRatioLimitMode(mode);
        });
    }

    void JniRpc::setTorrentRatioLimit(TorrentData& data, double limit)
    {
        data.ratioLimit = limit;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setRatioLimit(limit);
        });
    }

    void JniRpc::setTorrentPeersLimit(TorrentData& data, int limit)
    {
        data.peersLimit = limit;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setPeersLimit(limit);
        });
    }

    void JniRpc::setTorrentHonorSessionLimits(TorrentData& data, bool honor)
    {
        data.honorSessionLimits = honor;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setHonorSessionLimits(honor);
        });
    }

    void JniRpc::setTorrentBandwidthPriority(TorrentData& data, Torrent::Priority priority)
    {
        data.bandwidthPriority = priority;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setBandwidthPriority(priority);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimitMode(TorrentData& data, Torrent::IdleSeedingLimitMode mode)
    {
        data.idleSeedingLimitMode = mode;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimitMode(mode);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimit(TorrentData& data, int limit)
    {
        data.idleSeedingLimit = limit;
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimit(limit);
        });
    }

    void JniRpc::setTorrentFilesEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setFilesEnabled(enabled);
        });
    }

    void JniRpc::setTorrentFilesWanted(TorrentData& data, const QVariantList& files, bool wanted)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setFilesWanted(files, wanted);
        });
    }

    void JniRpc::setTorrentFilesPriority(TorrentData& data, const QVariantList& files, TorrentFile::Priority priority)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setFilesPriority(files, priority);
        });
    }

    void JniRpc::torrentAddTracker(TorrentData& data, const QString& announce)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->addTracker(announce);
        });
    }

    void JniRpc::torrentAddTrackers(TorrentData& data, const std::vector<QString>& announceUrls)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->addTrackers(announceUrls);
        });
    }

    void JniRpc::torrentSetTracker(TorrentData& data, int trackerId, const QString& announce)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setTracker(trackerId, announce);
        });
    }

    void JniRpc::torrentRemoveTrackers(TorrentData& data, const QVariantList& ids)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->removeTrackers(ids);
        });
    }

    void JniRpc::setTorrentPeersEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(&mRpc, data, [=](Torrent* torrent) {
            torrent->setPeersEnabled(enabled);
        });
    }

    void JniRpc::updateData()
    {
        QMetaObject::invokeMethod(&mRpc, "updateData");
    }
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*)
{
    static int argc = 0;
    static std::string arg;
    static std::array<char*, 1> argv { &arg.front() };
    new QCoreApplication(argc, argv.data());
    QCoreApplication::setApplicationName(QLatin1String("LibTremotesf"));
    return JNI_VERSION_1_2;
}

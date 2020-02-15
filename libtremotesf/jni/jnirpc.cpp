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
                    function(torrent.get());
                }
            });
        }
    }

    JniServerSettings::JniServerSettings(Rpc* rpc, QObject* parent)
        : ServerSettings(rpc, parent)
    {
        qRegisterMetaType<AlternativeSpeedLimitsDays>();
        qRegisterMetaType<EncryptionMode>();
    }

    void JniServerSettings::setDownloadDirectory(const QString& directory)
    {
        QMetaObject::invokeMethod(this, "setDownloadDirectory", Q_ARG(QString, directory));
    }

    void JniServerSettings::setStartAddedTorrents(bool start)
    {
        QMetaObject::invokeMethod(this, "setStartAddedTorrents", Q_ARG(bool, start));
    }

    void JniServerSettings::setTrashTorrentFiles(bool trash)
    {
        QMetaObject::invokeMethod(this, "setTrashTorrentFiles", Q_ARG(bool, trash));
    }

    void JniServerSettings::setRenameIncompleteFiles(bool rename)
    {
        QMetaObject::invokeMethod(this, "setRenameIncompleteFiles", Q_ARG(bool, rename));
    }

    void JniServerSettings::setIncompleteDirectoryEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setIncompleteDirectoryEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setIncompleteDirectory(const QString& directory)
    {
        QMetaObject::invokeMethod(this, "setIncompleteDirectory", Q_ARG(QString, directory));
    }

    void JniServerSettings::setRatioLimited(bool limited)
    {
        QMetaObject::invokeMethod(this, "setRatioLimited", Q_ARG(bool, limited));
    }

    void JniServerSettings::setRatioLimit(double limit)
    {
        QMetaObject::invokeMethod(this, "setRatioLimit", Q_ARG(double, limit));
    }

    void JniServerSettings::setIdleSeedingLimited(bool limited)
    {
        QMetaObject::invokeMethod(this, "setIdleSeedingLimited", Q_ARG(bool, limited));
    }

    void JniServerSettings::setIdleSeedingLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setIdleSeedingLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setDownloadQueueEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setDownloadQueueEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setDownloadQueueSize(int size)
    {
        QMetaObject::invokeMethod(this, "setDownloadQueueSize", Q_ARG(int, size));
    }

    void JniServerSettings::setSeedQueueEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setSeedQueueEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setSeedQueueSize(int size)
    {
        QMetaObject::invokeMethod(this, "setSeedQueueSize", Q_ARG(int, size));
    }

    void JniServerSettings::setIdleQueueLimited(bool limited)
    {
        QMetaObject::invokeMethod(this, "setIdleQueueLimited", Q_ARG(bool, limited));
    }

    void JniServerSettings::setIdleQueueLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setIdleQueueLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setDownloadSpeedLimited(bool limited)
    {
        QMetaObject::invokeMethod(this, "setDownloadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniServerSettings::setDownloadSpeedLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setDownloadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setUploadSpeedLimited(bool limited)
    {
        QMetaObject::invokeMethod(this, "setUploadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniServerSettings::setUploadSpeedLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setUploadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setAlternativeSpeedLimitsEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setAlternativeDownloadSpeedLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setAlternativeDownloadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setAlternativeUploadSpeedLimit(int limit)
    {
        QMetaObject::invokeMethod(this, "setAlternativeUploadSpeedLimit", Q_ARG(int, limit));
    }

    void JniServerSettings::setAlternativeSpeedLimitsScheduled(bool scheduled)
    {
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsScheduled", Q_ARG(bool, scheduled));
    }

    void JniServerSettings::setAlternativeSpeedLimitsBeginTime(const QTime& time)
    {
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsBeginTime", Q_ARG(QTime, time));
    }

    void JniServerSettings::setAlternativeSpeedLimitsEndTime(const QTime& time)
    {
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsEndTime", Q_ARG(QTime, time));
    }

    void JniServerSettings::setAlternativeSpeedLimitsDays(AlternativeSpeedLimitsDays days)
    {
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsDays", Q_ARG(libtremotesf::ServerSettings::AlternativeSpeedLimitsDays, days));
    }

    void JniServerSettings::setPeerPort(int port)
    {
        QMetaObject::invokeMethod(this, "setPeerPort", Q_ARG(int, port));
    }

    void JniServerSettings::setRandomPortEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setRandomPortEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setPortForwardingEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setPortForwardingEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setEncryptionMode(EncryptionMode mode)
    {
        QMetaObject::invokeMethod(this, "setEncryptionMode", Q_ARG(libtremotesf::ServerSettings::EncryptionMode, mode));
    }

    void JniServerSettings::setUtpEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setUtpEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setPexEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setPexEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setDhtEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setDhtEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setLpdEnabled(bool enabled)
    {
        QMetaObject::invokeMethod(this, "setLpdEnabled", Q_ARG(bool, enabled));
    }

    void JniServerSettings::setMaximumPeersPerTorrent(int peers)
    {
        QMetaObject::invokeMethod(this, "setMaximumPeersPerTorrent", Q_ARG(int, peers));
    }

    void JniServerSettings::setMaximumPeersGlobally(int peers)
    {
        QMetaObject::invokeMethod(this, "setMaximumPeersGlobally", Q_ARG(int, peers));
    }

    JniRpc::JniRpc()
        : Rpc(false)
    {
        qRegisterMetaType<Server>();
        qRegisterMetaType<Torrent::Priority>();
        qRegisterMetaType<Torrent::RatioLimitMode>();
        qRegisterMetaType<Torrent::IdleSeedingLimitMode>();
        qRegisterMetaType<TorrentFile::Priority>();

        setServerSettings(new JniServerSettings(this, this));

        setUpdateDisabled(true);

        QObject::connect(this, &Rpc::aboutToDisconnect, [=]() { onAboutToDisconnect(); });
        QObject::connect(this, &Rpc::statusChanged, [=]() { onStatusChanged(status()); });
        QObject::connect(this, &Rpc::errorChanged, [=]() { onErrorChanged(error(), errorMessage()); });

        QObject::connect(this, &Rpc::torrentsUpdated, [=](const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& t = this->torrents();
            onTorrentsUpdated(removed,
                              toNewPointers(t, changed.begin(), changed.end()),
                              toNewPointers(t, IndexIterator{t.size() - added}, IndexIterator{t.size()}));
        });

        QObject::connect(this, &Rpc::torrentFilesUpdated, [=](const Torrent* torrent, const std::vector<int>& changed) {
            onTorrentFilesUpdated(torrent->id(), toNewPointers(torrent->files(), changed.begin(), changed.end()));
        });
        QObject::connect(this, &Rpc::torrentPeersUpdated, [=](const Torrent* torrent, const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& p = torrent->peers();
            onTorrentPeersUpdated(torrent->id(),
                                  removed,
                                  toNewPointers(p, changed.begin(), changed.end()),
                                  toNewPointers(p, IndexIterator{p.size() - added}, IndexIterator{p.size()}));
        });

        QObject::connect(this, &Rpc::torrentFileRenamed, [=](int torrentId, const QString& filePath, const QString& newName) {
            onTorrentFileRenamed(torrentId, filePath, newName);
        });

        QObject::connect(this, &Rpc::torrentAdded, [=](const Torrent* torrent) {
            onTorrentAdded(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(this, &Rpc::torrentFinished, [=](const Torrent* torrent) {
            onTorrentFinished(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(this, &Rpc::torrentAddDuplicate, [=]() { onTorrentAddDuplicate(); });
        QObject::connect(this, &Rpc::torrentAddError, [=]() { onTorrentAddError(); });

        QObject::connect(this, &Rpc::gotDownloadDirFreeSpace, [=](long long bytes) { onGotDownloadDirFreeSpace(bytes); });
        QObject::connect(this, &Rpc::gotFreeSpaceForPath, [=](const QString& path, bool success, long long bytes) { onGotFreeSpaceForPath(path, success, bytes); });

        QObject::connect(this->serverStats(), &ServerStats::updated, [=]() { onServerStatsUpdated(); });

        auto thread = new QThread();
        thread->start();
        moveToThread(thread);
    }

    JniServerSettings* JniRpc::serverSettings() const
    {
        return static_cast<JniServerSettings*>(Rpc::serverSettings());
    }

    void JniRpc::setServer(const QString& name,
                           const QString& address,
                           int port,
                           const QString& apiPath,
                           bool https,
                           bool selfSignedCertificateEnabled,
                           const QByteArray& selfSignedCertificate,
                           bool clientCertificateEnabled,
                           const QByteArray& clientCertificate,
                           bool authentication,
                           const QString& username,
                           const QString& password,
                           int updateInterval,
                           int backgroundUpdateInterval,
                           int timeout)
    {
        Server server{name,
                      address,
                      port,
                      apiPath,
                      https,
                      selfSignedCertificateEnabled,
                      selfSignedCertificate,
                      clientCertificateEnabled,
                      clientCertificate,
                      authentication,
                      username,
                      password,
                      updateInterval,
                      backgroundUpdateInterval,
                      timeout};
        QMetaObject::invokeMethod(this, "setServer", Q_ARG(libtremotesf::Server, Server(std::move(server))));
    }

    void JniRpc::resetServer()
    {
        QMetaObject::invokeMethod(this, "resetServer");
    }

    void JniRpc::connect()
    {
        QMetaObject::invokeMethod(this, "connect");
    }

    void JniRpc::disconnect()
    {
        QMetaObject::invokeMethod(this, "disconnect");
    }

    void JniRpc::setUpdateDisabled(bool disabled)
    {
        QMetaObject::invokeMethod(this, "setUpdateDisabled", Q_ARG(bool, disabled));
    }

    void JniRpc::addTorrentFile(const QByteArray& fileData, const QString& downloadDirectory, const QVariantList& wantedFiles, const QVariantList& unwantedFiles, const QVariantList& highPriorityFiles, const QVariantList& normalPriorityFiles, const QVariantList& lowPriorityFiles, int bandwidthPriority, bool start)
    {
        QMetaObject::invokeMethod(this,
                                  "addTorrentFile",
                                  Q_ARG(QByteArray, fileData),
                                  Q_ARG(QString, downloadDirectory),
                                  Q_ARG(QVariantList, wantedFiles),
                                  Q_ARG(QVariantList, unwantedFiles),
                                  Q_ARG(QVariantList, highPriorityFiles),
                                  Q_ARG(QVariantList, normalPriorityFiles),
                                  Q_ARG(QVariantList, lowPriorityFiles),
                                  Q_ARG(int, bandwidthPriority),
                                  Q_ARG(bool, start));
    }

    void JniRpc::addTorrentLink(const QString& link, const QString& downloadDirectory, int bandwidthPriority, bool start)
    {
        QMetaObject::invokeMethod(this,
                                  "addTorrentLink",
                                  Q_ARG(QString, link),
                                  Q_ARG(QString, downloadDirectory),
                                  Q_ARG(int, bandwidthPriority),
                                  Q_ARG(bool, start));
    }

    void JniRpc::startTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "startTorrents", Q_ARG(QVariantList, ids));
    }

    /*void JniRpc::startTorrentsNow(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "startTorrentsNow", Q_ARG(QVariantList, ids));
    }*/

    void JniRpc::pauseTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "pauseTorrents", Q_ARG(QVariantList, ids));
    }

    void JniRpc::removeTorrents(const QVariantList& ids, bool deleteFiles)
    {
        QMetaObject::invokeMethod(this, "removeTorrents", Q_ARG(QVariantList, ids), Q_ARG(bool, deleteFiles));
    }

    void JniRpc::checkTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "checkTorrents", Q_ARG(QVariantList, ids));
    }

    /*void JniRpc::moveTorrentsToTop(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "moveTorrentsToTop", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsUp(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "moveTorrentsUp", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsDown(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "moveTorrentsDown", Q_ARG(QVariantList, ids));
    }

    void JniRpc::moveTorrentsToBottom(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "moveTorrentsToBottom", Q_ARG(QVariantList, ids));
    }*/

    void JniRpc::reannounceTorrents(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "reannounceTorrents", Q_ARG(QVariantList, ids));
    }

    void JniRpc::setTorrentsLocation(const QVariantList& ids, const QString& location, bool moveFiles)
    {
        QMetaObject::invokeMethod(this, "setTorrentsLocation", Q_ARG(QVariantList, ids), Q_ARG(QString, location), Q_ARG(bool, moveFiles));
    }

    void JniRpc::renameTorrentFile(int torrentId, const QString& filePath, const QString& newName)
    {
        QMetaObject::invokeMethod(this, "renameTorrentFile", Q_ARG(int, torrentId), Q_ARG(QString, filePath), Q_ARG(QString, newName));
    }

    void JniRpc::getDownloadDirFreeSpace()
    {
        QMetaObject::invokeMethod(this, "getDownloadDirFreeSpace");
    }

    void JniRpc::getFreeSpaceForPath(const QString& path)
    {
        QMetaObject::invokeMethod(this, "getFreeSpaceForPath", Q_ARG(QString, path));
    }

    void JniRpc::setTorrentDownloadSpeedLimited(TorrentData& data, bool limited)
    {
        data.downloadSpeedLimited = limited;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentDownloadSpeedLimit(TorrentData& data, int limit)
    {
        data.downloadSpeedLimit = limit;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimited(TorrentData& data, bool limited)
    {
        data.uploadSpeedLimited = limited;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimit(TorrentData& data, int limit)
    {
        data.uploadSpeedLimit = limit;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentRatioLimitMode(TorrentData& data, Torrent::RatioLimitMode mode)
    {
        data.ratioLimitMode = mode;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setRatioLimitMode(mode);
        });
    }

    void JniRpc::setTorrentRatioLimit(TorrentData& data, double limit)
    {
        data.ratioLimit = limit;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setRatioLimit(limit);
        });
    }

    void JniRpc::setTorrentPeersLimit(TorrentData& data, int limit)
    {
        data.peersLimit = limit;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setPeersLimit(limit);
        });
    }

    void JniRpc::setTorrentHonorSessionLimits(TorrentData& data, bool honor)
    {
        data.honorSessionLimits = honor;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setHonorSessionLimits(honor);
        });
    }

    void JniRpc::setTorrentBandwidthPriority(TorrentData& data, Torrent::Priority priority)
    {
        data.bandwidthPriority = priority;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setBandwidthPriority(priority);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimitMode(TorrentData& data, Torrent::IdleSeedingLimitMode mode)
    {
        data.idleSeedingLimitMode = mode;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimitMode(mode);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimit(TorrentData& data, int limit)
    {
        data.idleSeedingLimit = limit;
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimit(limit);
        });
    }

    void JniRpc::setTorrentFilesEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setFilesEnabled(enabled);
        });
    }

    void JniRpc::setTorrentFilesWanted(TorrentData& data, const QVariantList& files, bool wanted)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setFilesWanted(files, wanted);
        });
    }

    void JniRpc::setTorrentFilesPriority(TorrentData& data, const QVariantList& files, TorrentFile::Priority priority)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setFilesPriority(files, priority);
        });
    }

    void JniRpc::torrentAddTracker(TorrentData& data, const QString& announce)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->addTracker(announce);
        });
    }

    void JniRpc::torrentSetTracker(TorrentData& data, int trackerId, const QString& announce)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setTracker(trackerId, announce);
        });
    }

    void JniRpc::torrentRemoveTrackers(TorrentData& data, const QVariantList& ids)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->removeTrackers(ids);
        });
    }

    void JniRpc::setTorrentPeersEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(this, data, [=](Torrent* torrent) {
            torrent->setPeersEnabled(enabled);
        });
    }

    void JniRpc::updateData()
    {
        QMetaObject::invokeMethod(this, "updateData");
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

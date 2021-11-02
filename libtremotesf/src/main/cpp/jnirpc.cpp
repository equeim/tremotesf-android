#include "jnirpc.h"

#include <thread>

#include <QAbstractEventDispatcher>
#include <QCoreApplication>
#include <QElapsedTimer>

#include <android/log.h>

#include <jni.h>

Q_DECLARE_METATYPE(libtremotesf::Server)
Q_DECLARE_METATYPE(libtremotesf::TorrentFile::Priority)

namespace libtremotesf
{
    namespace
    {
        constexpr int threadStartTimeoutMs = 5000;
        constexpr const char* logTag = "LibTremotesf";

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
        std::vector<TorrentData*> toNewPointers(const std::vector<std::unique_ptr<Torrent>>& items, IndexIterator&& begin, IndexIterator&& end)
        {
            return toNewPointers<std::unique_ptr<Torrent>, TorrentData>(items, begin, end, [](const std::unique_ptr<Torrent>& i) { return i->data(); });
        }

        struct IndexIterator
        {
            size_t value;
            operator size_t() { return value; }
            size_t operator*() { return value; }
            IndexIterator& operator++() { ++value; return *this; }
        };
    }

    JniServerSettingsData::JniServerSettingsData(ServerSettings* settings)
        : ServerSettingsData(settings->data()),
          mSettings(settings)
    {
    }

    void JniServerSettingsData::setDownloadDirectory(const QString& directory)
    {
        downloadDirectory = directory;
        runOnThread([=] {
            mSettings->setDownloadDirectory(directory);
        });
    }

    void JniServerSettingsData::setStartAddedTorrents(bool start)
    {
        startAddedTorrents = start;
        runOnThread([=] {
            mSettings->setStartAddedTorrents(start);
        });
    }

    void JniServerSettingsData::setTrashTorrentFiles(bool trash)
    {
        trashTorrentFiles = trash;
        runOnThread([=] {
            mSettings->setTrashTorrentFiles(trash);
        });
    }

    void JniServerSettingsData::setRenameIncompleteFiles(bool rename)
    {
        renameIncompleteFiles = rename;
        runOnThread([=] {
            mSettings->setRenameIncompleteFiles(rename);
        });
    }

    void JniServerSettingsData::setIncompleteDirectoryEnabled(bool enabled)
    {
        incompleteDirectoryEnabled = enabled;
        runOnThread([=] {
            mSettings->setIncompleteDirectoryEnabled(enabled);
        });
    }

    void JniServerSettingsData::setIncompleteDirectory(const QString& directory)
    {
        incompleteDirectory = directory;
        runOnThread([=] {
            mSettings->setIncompleteDirectory(directory);
        });
    }

    void JniServerSettingsData::setRatioLimited(bool limited)
    {
        ratioLimited = limited;
        runOnThread([=] {
            mSettings->setRatioLimited(limited);
        });
    }

    void JniServerSettingsData::setRatioLimit(double limit)
    {
        ratioLimit = limit;
        runOnThread([=] {
            mSettings->setRatioLimit(limit);
        });
    }

    void JniServerSettingsData::setIdleSeedingLimited(bool limited)
    {
        idleSeedingLimited = limited;
        runOnThread([=] {
            mSettings->setIdleSeedingLimited(limited);
        });
    }

    void JniServerSettingsData::setIdleSeedingLimit(int limit)
    {
        idleSeedingLimit = limit;
        runOnThread([=] {
            mSettings->setIdleSeedingLimit(limit);
        });
    }

    void JniServerSettingsData::setDownloadQueueEnabled(bool enabled)
    {
        downloadQueueEnabled = enabled;
        runOnThread([=] {
            mSettings->setDownloadQueueEnabled(enabled);
        });
    }

    void JniServerSettingsData::setDownloadQueueSize(int size)
    {
        downloadQueueSize = size;
        runOnThread([=] {
            mSettings->setDownloadQueueSize(size);
        });
    }

    void JniServerSettingsData::setSeedQueueEnabled(bool enabled)
    {
        seedQueueEnabled = enabled;
        runOnThread([=] {
            mSettings->setSeedQueueEnabled(enabled);
        });
    }

    void JniServerSettingsData::setSeedQueueSize(int size)
    {
        seedQueueSize = size;
        runOnThread([=] {
            mSettings->setSeedQueueSize(size);
        });
    }

    void JniServerSettingsData::setIdleQueueLimited(bool limited)
    {
        idleQueueLimited = limited;
        runOnThread([=] {
            mSettings->setIdleQueueLimited(limited);
        });
    }

    void JniServerSettingsData::setIdleQueueLimit(int limit)
    {
        idleQueueLimit = limit;
        runOnThread([=] {
            mSettings->setIdleQueueLimit(limit);
        });
    }

    void JniServerSettingsData::setDownloadSpeedLimited(bool limited)
    {
        downloadSpeedLimited = limited;
        runOnThread([=] {
            mSettings->setDownloadSpeedLimited(limited);
        });
    }

    void JniServerSettingsData::setDownloadSpeedLimit(int limit)
    {
        downloadSpeedLimit = limit;
        runOnThread([=] {
            mSettings->setDownloadSpeedLimit(limit);
        });
    }

    void JniServerSettingsData::setUploadSpeedLimited(bool limited)
    {
        uploadSpeedLimited = limited;
        runOnThread([=] {
            mSettings->setUploadSpeedLimited(limited);
        });
    }

    void JniServerSettingsData::setUploadSpeedLimit(int limit)
    {
        uploadSpeedLimit = limit;
        runOnThread([=] {
            mSettings->setUploadSpeedLimit(limit);
        });
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsEnabled(bool enabled)
    {
        alternativeSpeedLimitsEnabled = enabled;
        runOnThread([=] {
            mSettings->setAlternativeSpeedLimitsEnabled(enabled);
        });
    }

    void JniServerSettingsData::setAlternativeDownloadSpeedLimit(int limit)
    {
        alternativeDownloadSpeedLimit = limit;
        runOnThread([=] {
            mSettings->setAlternativeDownloadSpeedLimit(limit);
        });
    }

    void JniServerSettingsData::setAlternativeUploadSpeedLimit(int limit)
    {
        alternativeUploadSpeedLimit = limit;
        runOnThread([=] {
            mSettings->setAlternativeUploadSpeedLimit(limit);
        });
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsScheduled(bool scheduled)
    {
        alternativeSpeedLimitsScheduled = scheduled;
        runOnThread([=] {
            mSettings->setAlternativeSpeedLimitsScheduled(scheduled);
        });
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsBeginTime(QTime time)
    {
        alternativeSpeedLimitsBeginTime = time;
        runOnThread([=] {
            mSettings->setAlternativeSpeedLimitsBeginTime(time);
        });
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsEndTime(QTime time)
    {
        alternativeSpeedLimitsEndTime = time;
        runOnThread([=] {
            mSettings->setAlternativeSpeedLimitsEndTime(time);
        });
    }

    void JniServerSettingsData::setAlternativeSpeedLimitsDays(ServerSettingsData::AlternativeSpeedLimitsDays days)
    {
        alternativeSpeedLimitsDays = days;
        runOnThread([=] {
            mSettings->setAlternativeSpeedLimitsDays(days);
        });
    }

    void JniServerSettingsData::setPeerPort(int port)
    {
        peerPort = port;
        runOnThread([=] {
            mSettings->setPeerPort(port);
        });
    }

    void JniServerSettingsData::setRandomPortEnabled(bool enabled)
    {
        randomPortEnabled = enabled;
        runOnThread([=] {
            mSettings->setRandomPortEnabled(enabled);
        });
    }

    void JniServerSettingsData::setPortForwardingEnabled(bool enabled)
    {
        portForwardingEnabled = enabled;
        runOnThread([=] {
            mSettings->setPortForwardingEnabled(enabled);
        });
    }

    void JniServerSettingsData::setEncryptionMode(ServerSettingsData::EncryptionMode mode)
    {
        encryptionMode = mode;
        runOnThread([=] {
            mSettings->setEncryptionMode(mode);
        });
    }

    void JniServerSettingsData::setUtpEnabled(bool enabled)
    {
        utpEnabled = enabled;
        runOnThread([=] {
            mSettings->setUtpEnabled(enabled);
        });
    }

    void JniServerSettingsData::setPexEnabled(bool enabled)
    {
        pexEnabled = enabled;
        runOnThread([=] {
            mSettings->setPexEnabled(enabled);
        });
    }

    void JniServerSettingsData::setDhtEnabled(bool enabled)
    {
        dhtEnabled = enabled;
        runOnThread([=] {
            mSettings->setDhtEnabled(enabled);
        });
    }

    void JniServerSettingsData::setLpdEnabled(bool enabled)
    {
        lpdEnabled = enabled;
        runOnThread([=] {
            mSettings->setLpdEnabled(enabled);
        });
    }

    void JniServerSettingsData::setMaximumPeersPerTorrent(int peers)
    {
        maximumPeersPerTorrent = peers;
        runOnThread([=] {
            mSettings->setMaximumPeersPerTorrent(peers);
        });
    }

    void JniServerSettingsData::setMaximumPeersGlobally(int peers)
    {
        maximumPeersGlobally = peers;
        runOnThread([=] {
            mSettings->setMaximumPeersGlobally(peers);
        });
    }

    template<typename Func>
    void JniServerSettingsData::runOnThread(Func&& function)
    {
        if (mSettings) {
            QMetaObject::invokeMethod(mSettings, std::forward<Func>(function));
        }
    }

    JniRpc::JniRpc()
    {
        qRegisterMetaType<Server>();
        qRegisterMetaType<Torrent::Priority>();
        qRegisterMetaType<Torrent::RatioLimitMode>();
        qRegisterMetaType<Torrent::IdleSeedingLimitMode>();
        qRegisterMetaType<TorrentFile::Priority>();
        qRegisterMetaType<ServerSettingsData::AlternativeSpeedLimitsDays>();
        qRegisterMetaType<ServerSettingsData::EncryptionMode>();
    }

    struct JniRpc::ThreadStartArgs {
        std::mutex mutex;
        std::condition_variable cv;
        bool applicationCreated{false};
    };

    void JniRpc::init()
    {
        __android_log_print(ANDROID_LOG_INFO, logTag, "init() called");
        __android_log_print(ANDROID_LOG_INFO, logTag, "init: starting Qt thread and waiting for QCoreApplication creation");

        auto args = std::make_shared<ThreadStartArgs>();

        std::thread thread(&JniRpc::exec, this, args);
        thread.detach();

        QElapsedTimer timer;
        timer.start();

        bool createdApplication;
        {
            std::unique_lock<std::mutex> lock(args->mutex);
            createdApplication = args->cv.wait_for(lock,
                                                   std::chrono::milliseconds(threadStartTimeoutMs),
                                                   [args] { return args->applicationCreated; });
        }
        const auto elapsed = timer.nsecsElapsed() / 1000000.0;
        if (!createdApplication) {
            __android_log_print(ANDROID_LOG_FATAL, logTag, "init: timed out waiting for QCoreApplication creation, elapsed time = %f ms", elapsed);
            std::terminate();
        }
        qInfo("init: created QCoreApplication, elapsed time = %f ms", elapsed);
    }

    void JniRpc::exec(std::shared_ptr<ThreadStartArgs> args)
    {
        int argc = 1;
        char argv0[] {'\0'};
        char* argv[] {argv0, nullptr};

        __android_log_print(ANDROID_LOG_INFO, logTag, "exec() called");
        __android_log_print(ANDROID_LOG_INFO, logTag, "exec: started Qt thread, creating QCoreApplication");

        new QCoreApplication(argc, argv);
        QCoreApplication::setApplicationName(QLatin1String(logTag));

        qInfo("exec: created QCoreApplication");

        {
            std::unique_lock<std::mutex> lock(args->mutex);
            args->applicationCreated = true;
        }
        args->cv.notify_one();
        args.reset();

        initRpc();

        qInfo("exec: calling QCoreApplication::exec");
        QCoreApplication::exec();
    }

    void JniRpc::initRpc()
    {
        qInfo("Initializing Rpc");

        mRpc = new Rpc();

        mRpc->setUpdateDisabled(true);

        QObject::connect(mRpc, &Rpc::aboutToDisconnect, [=]() { onAboutToDisconnect(); });
        QObject::connect(mRpc, &Rpc::connectionStateChanged, [=]() { onConnectionStateChanged(mRpc->connectionState()); });
        QObject::connect(mRpc, &Rpc::errorChanged, [=]() { onErrorChanged(mRpc->error(), mRpc->errorMessage()); });

        QObject::connect(mRpc->serverSettings(), &ServerSettings::changed, mRpc, [this] {
            onServerSettingsChanged(JniServerSettingsData(mRpc->serverSettings()));
        });

        QObject::connect(mRpc, &Rpc::torrentsUpdated, [=](const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& t = mRpc->torrents();
            onTorrentsUpdated(removed,
                              toNewPointers(t, changed.begin(), changed.end()),
                              toNewPointers(t, IndexIterator{t.size() - added}, IndexIterator{t.size()}));
        });

        QObject::connect(mRpc, &Rpc::torrentFilesUpdated, [=](const Torrent* torrent, const std::vector<int>& changed) {
            onTorrentFilesUpdated(torrent->id(), toNewPointers(torrent->files(), changed.begin(), changed.end()));
        });
        QObject::connect(mRpc, &Rpc::torrentPeersUpdated, [=](const Torrent* torrent, const std::vector<int>& removed, const std::vector<int>& changed, int added) {
            const auto& p = torrent->peers();
            onTorrentPeersUpdated(torrent->id(),
                                  removed,
                                  toNewPointers(p, changed.begin(), changed.end()),
                                  toNewPointers(p, IndexIterator{p.size() - added}, IndexIterator{p.size()}));
        });

        QObject::connect(mRpc, &Rpc::torrentFileRenamed, [=](int torrentId, const QString& filePath, const QString& newName) {
            onTorrentFileRenamed(torrentId, filePath, newName);
        });

        QObject::connect(mRpc, &Rpc::torrentAdded, [=](const Torrent* torrent) {
            onTorrentAdded(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(mRpc, &Rpc::torrentFinished, [=](const Torrent* torrent) {
            onTorrentFinished(torrent->id(), torrent->hashString(), torrent->name());
        });
        QObject::connect(mRpc, &Rpc::torrentAddDuplicate, [=]() { onTorrentAddDuplicate(); });
        QObject::connect(mRpc, &Rpc::torrentAddError, [=]() { onTorrentAddError(); });

        QObject::connect(mRpc, &Rpc::gotDownloadDirFreeSpace, [=](long long bytes) { onGotDownloadDirFreeSpace(bytes); });
        QObject::connect(mRpc, &Rpc::gotFreeSpaceForPath, [=](const QString& path, bool success, long long bytes) { onGotFreeSpaceForPath(path, success, bytes); });

        const auto stats = mRpc->serverStats();
        QObject::connect(stats, &ServerStats::updated, [=] {
            onServerStatsUpdated(stats->downloadSpeed(), stats->uploadSpeed(), stats->currentSession(), stats->total());
        });

        onServerSettingsChanged(JniServerSettingsData(mRpc->serverSettings()));

        qInfo("Initialized Rpc");
    }

    void JniRpc::setServer(const Server& server)
    {
        runOnThread([=] { mRpc->setServer(server); });
    }

    void JniRpc::resetServer()
    {
        runOnThread([=] { mRpc->resetServer(); });
    }

    void JniRpc::connect()
    {
        runOnThread([=] { mRpc->connect(); });
    }

    void JniRpc::disconnect()
    {
        runOnThread([=] { mRpc->disconnect(); });
    }

    void JniRpc::setUpdateDisabled(bool disabled)
    {
        runOnThread([=] { mRpc->setUpdateDisabled(disabled); });
    }

    void JniRpc::addTorrentFile(int fd, const QString& downloadDirectory, const QVariantList& unwantedFiles, const QVariantList& highPriorityFiles, const QVariantList& lowPriorityFiles, const std::unordered_map<QString, QString>& renamedFiles, int bandwidthPriority, bool start)
    {
        auto file(std::make_shared<QFile>());
        if (!file->open(fd, QIODevice::ReadOnly, QFileDevice::AutoCloseHandle)) {
            qWarning("Failed to open file by fd");
            return;
        }
        file->seek(0);
        QVariantMap renamed;
        for (const auto& i : renamedFiles) {
            renamed.insert(i.first, i.second);
        }
        runOnThread([=, file = std::move(file), renamed = std::move(renamed)]() mutable {
            mRpc->addTorrentFile(
                    std::move(file),
                    downloadDirectory,
                    unwantedFiles,
                    highPriorityFiles,
                    lowPriorityFiles,
                    renamed,
                    bandwidthPriority,
                    start);
        });
    }

    void JniRpc::addTorrentLink(const QString& link, const QString& downloadDirectory, int bandwidthPriority, bool start)
    {
        runOnThread([=] {
            mRpc->addTorrentLink(link, downloadDirectory, bandwidthPriority, start);
        });
    }

    void JniRpc::startTorrents(const QVariantList& ids)
    {
        runOnThread([=] { mRpc->startTorrents(ids); });
    }

    void JniRpc::startTorrentsNow(const QVariantList& ids)
    {
        runOnThread([=] { mRpc->startTorrentsNow(ids); });
    }

    void JniRpc::pauseTorrents(const QVariantList& ids)
    {
        runOnThread([=] { mRpc->pauseTorrents(ids); });
    }

    void JniRpc::removeTorrents(const QVariantList& ids, bool deleteFiles)
    {
        runOnThread([=] { mRpc->removeTorrents(ids, deleteFiles); });
    }

    void JniRpc::checkTorrents(const QVariantList& ids)
    {
        runOnThread([=] { mRpc->checkTorrents(ids); });
    }

    void JniRpc::reannounceTorrents(const QVariantList& ids)
    {
        runOnThread([=] { mRpc->reannounceTorrents(ids); });
    }

    void JniRpc::setTorrentsLocation(const QVariantList& ids, const QString& location, bool moveFiles)
    {
        runOnThread([=] { mRpc->setTorrentsLocation(ids, location, moveFiles); });
    }

    void JniRpc::renameTorrentFile(int torrentId, const QString& filePath, const QString& newName)
    {
        runOnThread([=] { mRpc->renameTorrentFile(torrentId, filePath, newName); });
    }

    void JniRpc::getDownloadDirFreeSpace()
    {
        runOnThread([=] { mRpc->getDownloadDirFreeSpace(); });
    }

    void JniRpc::getFreeSpaceForPath(const QString& path)
    {
        runOnThread([=] { mRpc->getFreeSpaceForPath(path); });
    }

    void JniRpc::setTorrentDownloadSpeedLimited(TorrentData& data, bool limited)
    {
        data.downloadSpeedLimited = limited;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentDownloadSpeedLimit(TorrentData& data, int limit)
    {
        data.downloadSpeedLimit = limit;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setDownloadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimited(TorrentData& data, bool limited)
    {
        data.uploadSpeedLimited = limited;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimited(limited);
        });
    }

    void JniRpc::setTorrentUploadSpeedLimit(TorrentData& data, int limit)
    {
        data.uploadSpeedLimit = limit;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setUploadSpeedLimit(limit);
        });
    }

    void JniRpc::setTorrentRatioLimitMode(TorrentData& data, Torrent::RatioLimitMode mode)
    {
        data.ratioLimitMode = mode;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setRatioLimitMode(mode);
        });
    }

    void JniRpc::setTorrentRatioLimit(TorrentData& data, double limit)
    {
        data.ratioLimit = limit;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setRatioLimit(limit);
        });
    }

    void JniRpc::setTorrentPeersLimit(TorrentData& data, int limit)
    {
        data.peersLimit = limit;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setPeersLimit(limit);
        });
    }

    void JniRpc::setTorrentHonorSessionLimits(TorrentData& data, bool honor)
    {
        data.honorSessionLimits = honor;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setHonorSessionLimits(honor);
        });
    }

    void JniRpc::setTorrentBandwidthPriority(TorrentData& data, Torrent::Priority priority)
    {
        data.bandwidthPriority = priority;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setBandwidthPriority(priority);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimitMode(TorrentData& data, Torrent::IdleSeedingLimitMode mode)
    {
        data.idleSeedingLimitMode = mode;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimitMode(mode);
        });
    }

    void JniRpc::setTorrentIdleSeedingLimit(TorrentData& data, int limit)
    {
        data.idleSeedingLimit = limit;
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setIdleSeedingLimit(limit);
        });
    }

    void JniRpc::setTorrentFilesEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setFilesEnabled(enabled);
        });
    }

    void JniRpc::setTorrentFilesWanted(TorrentData& data, const QVariantList& files, bool wanted)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setFilesWanted(files, wanted);
        });
    }

    void JniRpc::setTorrentFilesPriority(TorrentData& data, const QVariantList& files, TorrentFile::Priority priority)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setFilesPriority(files, priority);
        });
    }

    void JniRpc::torrentAddTrackers(TorrentData& data, const std::vector<QString>& announceUrls)
    {
        QStringList list;
        list.reserve(static_cast<int>(announceUrls.size()));
        for (const QString& url : announceUrls) {
            list.push_back(url);
        }
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->addTrackers(list);
        });
    }

    void JniRpc::torrentSetTracker(TorrentData& data, int trackerId, const QString& announce)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setTracker(trackerId, announce);
        });
    }

    void JniRpc::torrentRemoveTrackers(TorrentData& data, const QVariantList& ids)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->removeTrackers(ids);
        });
    }

    void JniRpc::setTorrentPeersEnabled(TorrentData& data, bool enabled)
    {
        runOnTorrent(data.id, [=](Torrent* torrent) {
            torrent->setPeersEnabled(enabled);
        });
    }

    void JniRpc::updateData()
    {
        runOnThread([=] { mRpc->updateData(); });
    }

    template<typename Func>
    void JniRpc::runOnThread(Func&& function)
    {
        QMetaObject::invokeMethod(QCoreApplication::eventDispatcher(), std::forward<Func>(function));
    }

    template<typename Func>
    void JniRpc::runOnTorrent(int torrentId, Func&& function)
    {
        runOnThread([=, f = std::forward<Func>(function)] {
            const auto torrent = mRpc->torrentById(torrentId);
            if (torrent) {
                f(torrent);
            }
        });
    }
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*)
{
    return JNI_VERSION_1_2;
}

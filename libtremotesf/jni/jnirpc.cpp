#include "jnirpc.h"

#include <QThread>
#include <QCoreApplication>

#include "rpc.h"
#include "serverstats.h"

#include <memory>

Q_DECLARE_METATYPE(libtremotesf::Server)

namespace libtremotesf
{
    namespace
    {
        int argc = 1;
        std::unique_ptr<char[]> arg(new char[1]{0});
        //std::unique_ptr<char*[]> argvU(new char*[1]{arg.get()});
        char* argR = arg.get();
        //char** argv = argvU.get();
        char** argv = &argR;
    }

    JniServerSettings::JniServerSettings(QObject* parent)
        : ServerSettings(nullptr, parent)
    {
        qDebug() << "wtf";
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

    void JniServerSettings::setRatioLimit(float limit)
    {
        QMetaObject::invokeMethod(this, "setRatioLimit", Q_ARG(float, limit));
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
        QMetaObject::invokeMethod(this, "setAlternativeSpeedLimitsDays", Q_ARG(AlternativeSpeedLimitsDays, days));
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
        QMetaObject::invokeMethod(this, "setEncryptionMode", Q_ARG(EncryptionMode, mode));
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
        : Rpc(new JniServerSettings())
    {
        qRegisterMetaType<Server>();
        qRegisterMetaType<Torrent::Priority>();
        qRegisterMetaType<Torrent::RatioLimitMode>();
        qRegisterMetaType<Torrent::IdleSeedingLimitMode>();

        serverSettings()->setParent(this);
        serverSettings()->setRpc(this);

        QObject::connect(this, &Rpc::connectedChanged, [=]() { onConnectedChanged(); });
        QObject::connect(this, &Rpc::statusChanged, [=]() { onStatusChanged(); });
        QObject::connect(this, &Rpc::errorChanged, [=]() { onErrorChanged(); });
        QObject::connect(this, &Rpc::torrentsUpdated, [=]() { onTorrentsUpdated(); });
        QObject::connect(this->serverStats(), &ServerStats::updated, [=]() { onServerStatsUpdated(); });
        QObject::connect(this, &Rpc::torrentFinished, [=](int id, const QString& hashString, const QString& name) {
            onTorrentFinished(id, hashString, name);
        });
        QObject::connect(this, &Rpc::torrentAddDuplicate, [=]() { onTorrentAddDuplicate(); });
        QObject::connect(this, &Rpc::torrentAddError, [=]() { onTorrentAddError(); });
        QObject::connect(this, &Rpc::gotTorrentFiles, [=](int torrentId) { onGotTorrentFiles(torrentId); });
        QObject::connect(this, &Rpc::torrentFileRenamed, [=](int torrentId, const QString& filePath, const QString& newName) {
            onTorrentFileRenamed(torrentId, filePath, newName);
        });

        QObject::connect(this, &Rpc::gotTorrentPeers, [=](int torrentId) { onGotTorrentPeers(torrentId); });

        QObject::connect(this, &Rpc::gotDownloadDirFreeSpace, [=](long long bytes) { onGotDownloadDirFreeSpace(bytes); });
        QObject::connect(this, &Rpc::gotFreeSpaceForPath, [=](const QString& path, bool success, long long bytes) { onGotFreeSpaceForPath(path, success, bytes); });
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

    bool JniRpc::backgroundUpdate() const
    {
        return this->backgroundUpdate();
    }

    void JniRpc::setBackgroundUpdate(bool background)
    {
        QMetaObject::invokeMethod(this, "setBackgroundUpdate", Q_ARG(bool, background));
    }

    bool JniRpc::isUpdateDisabled() const
    {
        return this->isUpdateDisabled();
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

    void JniRpc::startTorrentsNow(const QVariantList& ids)
    {
        QMetaObject::invokeMethod(this, "startTorrentsNow", Q_ARG(QVariantList, ids));
    }

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

    void JniRpc::moveTorrentsToTop(const QVariantList& ids)
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
    }

    void JniRpc::setTorrentLocation(int id, const QString& location, bool moveFiles)
    {
        QMetaObject::invokeMethod(this, "setTorrentLocation", Q_ARG(int, id), Q_ARG(QString, location), Q_ARG(bool, moveFiles));
    }

    void JniRpc::getTorrentFiles(int id, bool scheduled)
    {
        QMetaObject::invokeMethod(this, "getTorrentFiles", Q_ARG(int, id), Q_ARG(bool, scheduled));
    }

    void JniRpc::getTorrentPeers(int id, bool scheduled)
    {
        QMetaObject::invokeMethod(this, "getTorrentPeers", Q_ARG(int, id), Q_ARG(bool, scheduled));
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

    void JniRpc::setTorrentDownloadSpeedLimited(Torrent* torrent, bool limited)
    {
        QMetaObject::invokeMethod(torrent, "setDownloadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniRpc::setTorrentDownloadSpeedLimit(Torrent* torrent, int limit)
    {
        QMetaObject::invokeMethod(torrent, "setDownloadSpeedLimit", Q_ARG(int, limit));
    }

    void JniRpc::setTorrentUploadSpeedLimited(Torrent* torrent, bool limited)
    {
        QMetaObject::invokeMethod(torrent, "setUploadSpeedLimited", Q_ARG(bool, limited));
    }

    void JniRpc::setTorrentUploadSpeedLimit(Torrent* torrent, int limit)
    {
        QMetaObject::invokeMethod(torrent, "setUploadSpeedLimit", Q_ARG(int, limit));
    }

    void JniRpc::setTorrentRatioLimitMode(Torrent* torrent, Torrent::RatioLimitMode mode)
    {
        QMetaObject::invokeMethod(torrent, "setRatioLimitMode", Q_ARG(libtremotesf::Torrent::RatioLimitMode, mode));
    }

    void JniRpc::setTorrentRatioLimit(Torrent* torrent, float limit)
    {
        QMetaObject::invokeMethod(torrent, "setRatioLimit", Q_ARG(float, limit));
    }

    void JniRpc::setTorrentPeersLimit(Torrent* torrent, int limit)
    {
        QMetaObject::invokeMethod(torrent, "setPeersLimit", Q_ARG(int, limit));
    }

    void JniRpc::setTorrentHonorSessionLimits(Torrent* torrent, bool honor)
    {
        QMetaObject::invokeMethod(torrent, "setHonorSessionLimits", Q_ARG(bool, honor));
    }

    void JniRpc::setTorrentBandwidthPriority(Torrent* torrent, Torrent::Priority priority)
    {
        QMetaObject::invokeMethod(torrent, "setBandwidthPriority", Q_ARG(libtremotesf::Torrent::Priority, priority));
    }

    void JniRpc::setTorrentIdleSeedingLimitMode(Torrent* torrent, Torrent::IdleSeedingLimitMode mode)
    {
        QMetaObject::invokeMethod(torrent, "setIdleSeedingLimitMode", Q_ARG(libtremotesf::Torrent::IdleSeedingLimitMode, mode));
    }

    void JniRpc::setTorrentIdleSeedingLimit(Torrent* torrent, int limit)
    {
        QMetaObject::invokeMethod(torrent, "setIdleSeedingLimit", Q_ARG(int, limit));
    }

    void JniRpc::setTorrentFilesEnabled(Torrent* torrent, bool enabled)
    {
        QMetaObject::invokeMethod(torrent, "setFilesEnabled", Q_ARG(bool, enabled));
    }

    void JniRpc::setTorrentFilesWanted(Torrent* torrent, const QVariantList& files, bool wanted)
    {
        QMetaObject::invokeMethod(torrent, "setFilesWanted", Q_ARG(QVariantList, files), Q_ARG(bool, wanted));
    }

    void JniRpc::setTorrentFilesPriority(Torrent* torrent, const QVariantList& files, TorrentFile::Priority priority)
    {
        QMetaObject::invokeMethod(torrent, "setFilesPriority", Q_ARG(QVariantList, files), Q_ARG(libtremotesf::TorrentFile::Priority, priority));
    }

    void JniRpc::torrentRenameFile(Torrent* torrent, const QString& path, const QString& newName)
    {
        QMetaObject::invokeMethod(torrent, "renameFile", Q_ARG(QString, path), Q_ARG(QString, newName));
    }

    void JniRpc::torrentAddTracker(Torrent* torrent, const QString& announce)
    {
        QMetaObject::invokeMethod(torrent, "addTracker", Q_ARG(QString, announce));
    }

    void JniRpc::torrentSetTracker(Torrent* torrent, int trackerId, const QString& announce)
    {
        QMetaObject::invokeMethod(torrent, "setTracker", Q_ARG(int, trackerId), Q_ARG(QString, announce));
    }

    void JniRpc::torrentRemoveTrackers(Torrent* torrent, const QVariantList& ids)
    {
        QMetaObject::invokeMethod(torrent, "removeTrackers", Q_ARG(QVariantList, ids));
    }

    void JniRpc::setTorrentPeersEnabled(Torrent* torrent, bool enabled)
    {
        QMetaObject::invokeMethod(torrent, "setPeersEnabled", Q_ARG(bool, enabled));
    }

    void JniRpc::onConnectedChanged()
    {

    }

    void JniRpc::onStatusChanged()
    {

    }

    void JniRpc::onErrorChanged()
    {

    }

    void JniRpc::onTorrentsUpdated()
    {

    }

    void JniRpc::onServerStatsUpdated()
    {

    }

    void JniRpc::onTorrentFinished(int id, const QString& hashString, const QString& name)
    {

    }

    void JniRpc::onTorrentAddDuplicate()
    {

    }

    void JniRpc::onTorrentAddError()
    {

    }

    void JniRpc::onGotTorrentFiles(int torrentId)
    {

    }

    void JniRpc::onTorrentFileRenamed(int torrentId, const QString& filePath, const QString& newName)
    {

    }

    void JniRpc::onGotTorrentPeers(int torrentId)
    {

    }

    void JniRpc::onGotDownloadDirFreeSpace(long long bytes)
    {

    }

    void JniRpc::onGotFreeSpaceForPath(const QString& path, bool success, long long bytes)
    {

    }

    JniWrapper::JniWrapper(JniRpc* rpc)
        : mApp(new QCoreApplication(argc, argv)),
          mThread(new QThread())
    {
        rpc->moveToThread(mThread);
        /*QObject::connect(mThread, &QThread::finished, [=]() {
            mRpc->deleteLater();
            mRpc = nullptr;
        });*/
        mThread->start();
    }

    JniWrapper::~JniWrapper()
    {
        mThread->quit();
        mThread->wait();
        mApp->deleteLater();
    }
}

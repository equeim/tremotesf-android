#ifndef LIBTREMOTESF_JNIRPC_H
#define LIBTREMOTESF_JNIRPC_H

#include <unordered_map>

#include <QThread>

#include "../libtremotesf/rpc.h"
#include "../libtremotesf/serversettings.h"
#include "../libtremotesf/serverstats.h"
#include "../libtremotesf/stdutils.h"
#include "../libtremotesf/torrent.h"

namespace libtremotesf
{
    struct JniServerSettingsData : ServerSettingsData
    {
    public:
        JniServerSettingsData() = default;
        explicit JniServerSettingsData(ServerSettings* settings);

        void setDownloadDirectory(const QString& directory);
        void setStartAddedTorrents(bool start);
        void setTrashTorrentFiles(bool trash);
        void setRenameIncompleteFiles(bool rename);
        void setIncompleteDirectoryEnabled(bool enabled);
        void setIncompleteDirectory(const QString& directory);
        void setRatioLimited(bool limited);
        void setRatioLimit(double limit);
        void setIdleSeedingLimited(bool limited);
        void setIdleSeedingLimit(int limit);
        void setDownloadQueueEnabled(bool enabled);
        void setDownloadQueueSize(int size);
        void setSeedQueueEnabled(bool enabled);
        void setSeedQueueSize(int size);
        void setIdleQueueLimited(bool limited);
        void setIdleQueueLimit(int limit);
        void setDownloadSpeedLimited(bool limited);
        void setDownloadSpeedLimit(int limit);
        void setUploadSpeedLimited(bool limited);
        void setUploadSpeedLimit(int limit);
        void setAlternativeSpeedLimitsEnabled(bool enabled);
        void setAlternativeDownloadSpeedLimit(int limit);
        void setAlternativeUploadSpeedLimit(int limit);
        void setAlternativeSpeedLimitsScheduled(bool scheduled);
        void setAlternativeSpeedLimitsBeginTime(QTime time);
        void setAlternativeSpeedLimitsEndTime(QTime time);
        void setAlternativeSpeedLimitsDays(ServerSettingsData::AlternativeSpeedLimitsDays days);
        void setPeerPort(int port);
        void setRandomPortEnabled(bool enabled);
        void setPortForwardingEnabled(bool enabled);
        void setEncryptionMode(ServerSettingsData::EncryptionMode mode);
        void setUtpEnabled(bool enabled);
        void setPexEnabled(bool enabled);
        void setDhtEnabled(bool enabled);
        void setLpdEnabled(bool enabled);
        void setMaximumPeersPerTorrent(int peers);
        void setMaximumPeersGlobally(int peers);

    private:
        template<typename Func>
        void runOnThread(Func&& function);

        ServerSettings* mSettings = nullptr;
    };

    class JniRpc
    {
    public:
        JniRpc();
        virtual ~JniRpc() = default;

        void setServer(const Server& server);
        void resetServer();

        void connect();
        void disconnect();

        void setUpdateDisabled(bool disabled);

        void addTorrentFile(int fd,
                            const QString& downloadDirectory,
                            const QVariantList& unwantedFiles,
                            const QVariantList& highPriorityFiles,
                            const QVariantList& lowPriorityFiles,
                            const std::unordered_map<QString, QString>& renamedFiles,
                            int bandwidthPriority,
                            bool start);

        void addTorrentLink(const QString& link,
                            const QString& downloadDirectory,
                            int bandwidthPriority,
                            bool start);

        void startTorrents(const QVariantList& ids);
        void pauseTorrents(const QVariantList& ids);
        void removeTorrents(const QVariantList& ids, bool deleteFiles);
        void checkTorrents(const QVariantList& ids);

        void reannounceTorrents(const QVariantList& ids);

        void setTorrentsLocation(const QVariantList& ids, const QString& location, bool moveFiles);

        void renameTorrentFile(int torrentId,
                               const QString& filePath,
                               const QString& newName);

        void getDownloadDirFreeSpace();
        void getFreeSpaceForPath(const QString& path);

        void setTorrentDownloadSpeedLimited(TorrentData& data, bool limited);
        void setTorrentDownloadSpeedLimit(TorrentData& data, int limit);
        void setTorrentUploadSpeedLimited(TorrentData& data, bool limited);
        void setTorrentUploadSpeedLimit(TorrentData& data, int limit);
        void setTorrentRatioLimitMode(TorrentData& data, Torrent::RatioLimitMode mode);
        void setTorrentRatioLimit(TorrentData& data, double limit);
        void setTorrentPeersLimit(TorrentData& data, int limit);
        void setTorrentHonorSessionLimits(TorrentData& data, bool honor);
        void setTorrentBandwidthPriority(TorrentData& data, Torrent::Priority priority);
        void setTorrentIdleSeedingLimitMode(TorrentData& data, Torrent::IdleSeedingLimitMode mode);
        void setTorrentIdleSeedingLimit(TorrentData& data, int limit);
        void setTorrentFilesEnabled(TorrentData& data, bool enabled);
        void setTorrentFilesWanted(TorrentData& data, const QVariantList& files, bool wanted);
        void setTorrentFilesPriority(TorrentData& data, const QVariantList& files, TorrentFile::Priority priority);
        void torrentAddTrackers(TorrentData& data, const std::vector<QString>& announceUrls);
        void torrentSetTracker(TorrentData& data, int trackerId, const QString& announce);
        void torrentRemoveTrackers(TorrentData& data, const QVariantList& ids);
        void setTorrentPeersEnabled(TorrentData& data, bool enabled);

        void updateData();

    protected:
        virtual void onAboutToDisconnect() = 0;
        virtual void onStatusChanged(Rpc::Status status) = 0;
        virtual void onErrorChanged(Rpc::Error error, const QString& errorMessage) = 0;

        virtual void onServerSettingsChanged(JniServerSettingsData data) = 0;

        virtual void onTorrentsUpdated(const std::vector<int>& removed, const std::vector<TorrentData*>& changed, const std::vector<TorrentData*>& added) = 0;

        virtual void onTorrentFilesUpdated(int torrentId, const std::vector<TorrentFile*>& changed) = 0;
        virtual void onTorrentPeersUpdated(int torrentId, const std::vector<int>& removed, const std::vector<Peer*>& changed, const std::vector<Peer*>& added) = 0;

        virtual void onServerStatsUpdated(long long downloadSpeed, long long uploadSpeed, SessionStats currentSession, SessionStats total) = 0;

        virtual void onTorrentAdded(int id, const QString& hashString, const QString& name) = 0;
        virtual void onTorrentFinished(int id, const QString& hashString, const QString& name) = 0;

        virtual void onTorrentAddDuplicate() = 0;
        virtual void onTorrentAddError() = 0;

        virtual void onTorrentFileRenamed(int torrentId, const QString& filePath, const QString& newName) = 0;

        virtual void onGotDownloadDirFreeSpace(long long bytes) = 0;
        virtual void onGotFreeSpaceForPath(const QString& path, bool success, long long bytes) = 0;

    private:
        template<typename Func>
        void runOnThread(Func&& function);

        template<typename Func>
        void runOnTorrent(int torrentId, Func&& function);

        void initRpc();

        QThread mThread;
        Rpc* mRpc = nullptr;
    };
}

#endif // LIBTREMOTESF_JNIRPC_H

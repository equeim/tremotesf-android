package org.equeim.libtremotesf.javacpp;

import org.bytedeco.javacpp.annotation.NoException;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.Info;
import org.bytedeco.javacpp.tools.InfoMap;
import org.bytedeco.javacpp.tools.InfoMapper;

@Properties(
        value = @Platform(
                compiler = "cpp17",
                define = {"SHARED_PTR_NAMESPACE std", "UNIQUE_PTR_NAMESPACE std"},
                include = {"libtremotesf/qtsupport.h", "libtremotesf/peer.h", "libtremotesf/rpc.h", "libtremotesf/serversettings.h", "libtremotesf/serverstats.h", "libtremotesf/torrent.h", "libtremotesf/torrentfile.h", "libtremotesf/tracker.h", "jnirpc.h", "javacpputils.h"}
        ),
        target = "org.equeim.libtremotesf",
        global = "LibTremotesf"
)
//@NoException
public class LibTremotesfConfig implements InfoMapper {
    @Override
    public void map(InfoMap infoMap) {
        infoMap
                .put(new Info("QString").annotations("@Adapter(\"QStringAdapter\")", "@Cast({\"\", \"QString&\"})", "@AsUtf16").valueTypes("String"))
                .put(new Info("std::vector<QString>").pointerTypes("StringVector").define())
                .put(new Info("QByteArray").annotations("@Adapter(\"QByteArrayAdapter\")", "@Cast({\"char*\", \"QByteArray&\"})").valueTypes("byte[]"))
                .put(new Info("QTime").annotations("@Cast(\"QTimeConverter\")").valueTypes("int"))
                .put(new Info("signals").cppText("#define signals private"))
                .put(new Info("Q_OBJECT").cppText("#define Q_OBJECT"))
                .put(new Info("Q_GADGET").cppText("#define Q_GADGET"))
                .put(new Info("Q_INVOKABLE").cppText("#define Q_INVOKABLE"))
                .put(new Info("Q_PROPERTY").cppText("#define Q_PROPERTY(arg0)"))
                .put(new Info("Q_ENUM").cppText("#define Q_ENUM(arg0)"))
                .put(new Info("Q_NAMESPACE").cppText("#define Q_NAMESPACE"))
                .put(new Info("QT_VERSION_CHECK").cppText("#define QT_VERSION_CHECK(major, minor, patch) ((major<<16)|(minor<<8)|(patch))"))
                .put(new Info("QT_VERSION").cppText("#define QT_VERSION QT_VERSION_CHECK(5, 15, 2)"))
                .put(new Info("QT_BEGIN_INCLUDE_NAMESPACE").cppText("#define QT_BEGIN_INCLUDE_NAMESPACE"))
                .put(new Info("QT_END_INCLUDE_NAMESPACE").cppText("#define QT_END_INCLUDE_NAMESPACE"))
                .put(new Info("QT_PREPEND_NAMESPACE").cppText("#define QT_PREPEND_NAMESPACE(arg0)"))

                .put(new Info("libtremotesf::Server").beanify())
                .put(new Info("libtremotesf::Server::ProxyType").enumerate())

                .put(new Info("libtremotesf::RpcConnectionState").enumerate())
                .put(new Info("libtremotesf::RpcError").enumerate())
                .put(new Info("libtremotesf::ServerSettingsData").purify().immutable().beanify())
                .put(new Info("libtremotesf::JniServerSettingsData").translate())
                .put(new Info("libtremotesf::SessionStats"))
                .put(new Info("libtremotesf::TorrentData").immutable().beanify())
                .put(new Info("std::vector<libtremotesf::TorrentData>").pointerTypes("TorrentDataVector").define())
                .put(new Info("libtremotesf::moveConstruct<libtremotesf::TorrentData>").javaNames("moveConstructTorrentData").define())

                .put(new Info("libtremotesf::Tracker").purify())
                .put(new Info("libtremotesf::Tracker::operator==").skip())

                .put(new Info("libtremotesf::Peer").immutable().beanify())
                .put(new Info("libtremotesf::Peer::operator==").skip())
                .put(new Info("std::vector<libtremotesf::Peer>").pointerTypes("PeerVector").define())
                .put(new Info("libtremotesf::moveConstruct<libtremotesf::Peer>").javaNames("moveConstructPeer").define())

                .put(new Info("libtremotesf::TorrentFile").immutable().beanify())
                .put(new Info("std::vector<libtremotesf::TorrentFile>").pointerTypes("TorrentFileVector").define())
                .put(new Info("libtremotesf::moveConstruct<libtremotesf::TorrentFile>").javaNames("moveConstructTorrentFile").define())

                .put(new Info("int").valueTypes("int").pointerTypes("int[]"))
                .put(new Info("long long").valueTypes("long").pointerTypes("long[]").cast())

                .put(new Info("std::unordered_map<QString,QString>").pointerTypes("StringStringMap").define())
                .put(new Info("libtremotesf::JniRpc").virtualize())
                .put(new Info("libtremotesf::ServerSettingsData::AlternativeSpeedLimitsDays").enumerate())
                .put(new Info("libtremotesf::ServerSettingsData::EncryptionMode").enumerate())
                .put(new Info("libtremotesf::TorrentData::Status").enumerate())
                .put(new Info("libtremotesf::TorrentData::Priority").enumerate().valueTypes("org.equeim.libtremotesf.TorrentData.Priority"))
                .put(new Info("libtremotesf::TorrentData::RatioLimitMode").enumerate().valueTypes("org.equeim.libtremotesf.TorrentData.RatioLimitMode"))
                .put(new Info("libtremotesf::TorrentData::IdleSeedingLimitMode").enumerate().valueTypes("org.equeim.libtremotesf.TorrentData.IdleSeedingLimitMode"))
                .put(new Info("libtremotesf::TorrentFile::Priority").enumerate().valueTypes("org.equeim.libtremotesf.TorrentFile.Priority"))
                .put(new Info("libtremotesf::Tracker::Status").enumerate());

        skip(infoMap);
    }

    private void skip(InfoMap infoMap) {
        final String[] names = new String[]{
                "QAuthenticator",
                "QDateTime",
                "QJsonObject",
                "QObject",
                "QNetworkAccessManager",
                "QNetworkReply",
                "QTimer",
                "std::hash",

                "QStringAdapter",
                "QByteArrayAdapter",
                "QTimeConverter",
                "QJsonKeyString",
                "QJsonKeyStringInit",
                "libtremotesf::ServerSettings",
                "libtremotesf::ServerStats",
                "libtremotesf::Torrent",
                "libtremotesf::Rpc"
        };
        for (String name : names) {
            infoMap.put(new Info(name).skip());
        }
    }
}

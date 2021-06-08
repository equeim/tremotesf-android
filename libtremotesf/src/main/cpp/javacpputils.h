#ifndef LIBTREMOTESF_JAVACPPUTILS_H
#define LIBTREMOTESF_JAVACPPUTILS_H

#include <algorithm>
#include <string>
#include <QString>
#include <QTime>
#include <QDebug>

class __attribute__((visibility("hidden"))) QStringAdapter {
public:
    inline QStringAdapter(const unsigned short* ptr, int size, void* owner) : str{str2} { assign(const_cast<unsigned short*>(ptr), size, owner); }

    inline QStringAdapter(const QString& str) : str2{str}, str{str2} {}
    inline QStringAdapter(QString& str) : str{str} {}
    inline QStringAdapter(const QString* str) : str{*const_cast<QString*>(str)} {}

    static inline void deallocate(void* owner) { delete[] static_cast<unsigned short*>(owner); }

    inline operator unsigned short*() {
        const auto* data = str.utf16();
        if (str.size() > size) {
            ptr = new (std::nothrow) unsigned short[static_cast<size_t>(str.size() + 1)]{};
        }
        if (ptr && std::char_traits<unsigned short>::compare(ptr, data, static_cast<size_t>(str.size())) != 0) {
            std::copy(data, data + str.size(), ptr);
            if (size > str.size()) {
                ptr[str.size()] = 0;
            }
        }
        size = str.size();
        owner = ptr;
        return ptr;
    }

    inline operator const unsigned short*() {
        size = str.size();
        return str.utf16();
    }

    inline operator QString&() { return str; }
    inline operator QString*() { return ptr ? &str : nullptr; }

    inline void assign(unsigned short* ptr, int size, void* owner) {
        this->ptr = ptr;
        this->size = size;
        this->owner = owner;
        if (ptr) {
            qInfo() << "Setting QString to" << QString::fromUtf16(ptr);
            str.setUtf16(ptr, size > 0 ? size : static_cast<int>(std::char_traits<unsigned short>::length(ptr)));
        } else {
            str.clear();
        }
    }

    int size = 0;
    void* owner = nullptr;

private:
    unsigned short* ptr = nullptr;
    QString str2;
    QString& str;
};

class __attribute__((visibility("hidden"))) QByteArrayAdapter {
public:
    inline QByteArrayAdapter(const char* ptr, int size, void* owner) : str{str2} { assign(const_cast<char*>(ptr), size, owner); }

    inline QByteArrayAdapter(const QByteArray& str) : str2{str}, str{str2} {}
    inline QByteArrayAdapter(QByteArray& str) : str{str} {}
    inline QByteArrayAdapter(const QByteArray* str) : str{*const_cast<QByteArray*>(str)} {}

    static inline void deallocate(void* owner) { delete[] static_cast<char*>(owner); }

    inline operator char*() {
        const auto* data = str.constData();
        if (str.size() > size) {
            ptr = new (std::nothrow) char[static_cast<size_t>(str.size() + 1)]{};
        }
        if (ptr && std::char_traits<char>::compare(ptr, data, static_cast<size_t>(str.size())) != 0) {
            std::copy(data, data + str.size(), ptr);
            if (size > str.size()) {
                ptr[str.size()] = 0;
            }
        }
        size = str.size();
        owner = ptr;
        return ptr;
    }

    inline operator const char*() {
        size = str.size();
        return str.constData();
    }

    inline operator QByteArray&() { return str; }
    inline operator QByteArray*() { return ptr ? &str : nullptr; }

    inline void assign(char* ptr, int size, void* owner) {
        this->ptr = ptr;
        this->size = size;
        this->owner = owner;
        if (ptr) {
            str = QByteArray(ptr, size > 0 ? size : static_cast<int>(std::char_traits<char>::length(ptr)));
        } else {
            str.clear();
        }
    }

    int size = 0;
    void* owner = nullptr;

private:
    char* ptr = nullptr;
    QByteArray str2;
    QByteArray& str;
};

class __attribute__((visibility("hidden"))) QTimeConverter {
public:
    inline QTimeConverter(int minutesSinceStartOfDay) : time{QTime::fromMSecsSinceStartOfDay(minutesSinceStartOfDay * 60000)} {}
    inline QTimeConverter(QTime time) : time{time} {}

    inline operator int() { return time.msecsSinceStartOfDay() / 60000; }
    inline operator QTime() { return time; }

private:
    QTime time;
};

namespace libtremotesf {
    template<typename T>
    inline T moveConstruct(T& other) { return std::move(other); }
}

#endif // LIBTREMOTESF_JAVACPPUTILS_H
// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

%naturalvar QDateTime;

class QDateTime;

// QDateTime
%typemap(jni) QDateTime "jlong"
%typemap(jtype) QDateTime "long"
%typemap(jstype) QDateTime "@androidx.annotation.Nullable org.threeten.bp.Instant"
%typemap(javain) QDateTime "org.equeim.libtremotesf.libtremotesfJNI.instantToMillis($javainput)"
%typemap(javaout) QDateTime { return org.equeim.libtremotesf.libtremotesfJNI.millisToInstant($jnicall); }
%typemap(javadirectorin) QDateTime "org.equeim.libtremotesf.libtremotesfJNI.millisToInstant($jniinput)"
%typemap(javadirectorout) QDateTime "org.equeim.libtremotesf.libtremotesfJNI.instantToMillis($javacall)"

%typemap(in) QDateTime
%{
    if ($input == 0) {
        $1 = QDateTime({}, {}, Qt::UTC);
    } else {
        $1 = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
    }
%}

%typemap(directorout) QDateTime
%{
    if ($input == 0) {
        $result = QDateTime({}, {}, Qt::UTC);
    } else {
        $result = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
    }
%}

%typemap(directorin,descriptor="J") QDateTime
%{
    if ($1.isValid()) {
        $input = static_cast<jlong>($1.toMSecsSinceEpoch());
    } else {
        $input = 0;
    }
%}

%typemap(out) QDateTime
%{
    if ($1.isValid()) {
        $result = static_cast<jlong>($1.toMSecsSinceEpoch());
    } else {
        $result = 0;
    }
%}

%typemap(jni) const QDateTime& "jlong"
%typemap(jtype) const QDateTime& "long"
%typemap(jstype) const QDateTime& "@androidx.annotation.Nullable org.threeten.bp.Instant"
%typemap(javain) const QDateTime& "org.equeim.libtremotesf.libtremotesfJNI.instantToMillis($javainput)"
%typemap(javaout) const QDateTime& { return org.equeim.libtremotesf.libtremotesfJNI.millisToInstant($jnicall); }
%typemap(javadirectorin) const QDateTime& "org.equeim.libtremotesf.libtremotesfJNI.millisToInstant($jniinput)"
%typemap(javadirectorout) const QDateTime& "org.equeim.libtremotesf.libtremotesfJNI.instantToMillis($javacall)"

%typemap(in) const QDateTime&
%{
    auto $1_value = [&] {
        if ($input == 0) {
            return QDateTime({}, {}, Qt::UTC);
        } else {
            return QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
        }
    }();
    $1 = &$1_value;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QDateTime&
%{
    /* possible thread/reentrant code problem */
    static QDateTime $1_static{};
    if ($input == 0) {
        $1_static = QDateTime({}, {}, Qt::UTC);
    } else {
        $1_static = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
    }
    $result = &$1_static;
%}

%typemap(directorin,descriptor="J") const QDateTime&
%{
    if ($1.isValid()) {
        $input = static_cast<jlong>($1.toMSecsSinceEpoch());
    } else {
        $input = 0;
    }
%}

%typemap(out) const QDateTime& 
%{
    if ($1->isValid()) {
        $result = static_cast<jlong>($1->toMSecsSinceEpoch());
    } else {
        $result = 0;
    }
%}

%pragma(java) jniclasscode=%{
  public final static long instantToMillis(@androidx.annotation.Nullable org.threeten.bp.Instant instant) {
    return instant == null ? 0 : instant.toEpochMilli();
  }

  public final static @androidx.annotation.Nullable org.threeten.bp.Instant millisToInstant(long millis) {
    return millis == 0 ? null : org.threeten.bp.Instant.ofEpochMilli(millis);
  }
%}

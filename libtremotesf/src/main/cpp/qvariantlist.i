%naturalvar QVariantList;

class QVariantList;

// QVariantList
%typemap(jni) QVariantList "jintArray"
%typemap(jtype) QVariantList "int[]"
%typemap(jstype) QVariantList "int[]"
%typemap(javadirectorin) QVariantList "$jniinput"
%typemap(javadirectorout) QVariantList "$javacall"

%typemap(in) QVariantList
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QVariantList");
        return $null;
    }
    jint* $1_pstr = jenv->GetIntArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    if ($1_len) {
        $1.reserve($1_len);
        for (jsize i = 0; i < $1_len; ++i) {
            $1.push_back($1_pstr[i]);
        }
    }
    jenv->ReleaseIntArrayElements($input, $1_pstr, 0);
%}

%typemap(directorout) QVariantList
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QVariantList");
        return $null;
    }
    jint* $1_pstr = jenv->GetIntArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    if ($1_len) {
        $result = QVariantList();
        $result.reserve($1_len);
        for (jsize i = 0; i < $1_len; ++i) {
            $result.push_back($1_pstr[i]);
        }
    }
    jenv->ReleaseIntArrayElements($input, $1_pstr, 0);
%}

%typemap(javain) QVariantList "$javainput"

%typemap(javaout) QVariantList
{
    return $jnicall;
}

%typemap(jni) const QVariantList& "jintArray"
%typemap(jtype) const QVariantList& "int[]"
%typemap(jstype) const QVariantList& "int[]"
%typemap(javadirectorin) const QVariantList& "$jniinput"
%typemap(javadirectorout) const QVariantList& "$javacall"

%typemap(in) const QVariantList&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QVariantList");
        return $null;
    }
    jint* $1_pstr = jenv->GetIntArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    QVariantList $1_str;
    if ($1_len) {
        $1_str.reserve($1_len);
        for (jsize i = 0; i < $1_len; ++i) {
            $1_str.push_back($1_pstr[i]);
        }
    }
    $1 = &$1_str;
    jenv->ReleaseIntArrayElements($input, $1_pstr, 0);
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QVariantList& 
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QVariantList");
        return $null;
    }
    jint* $1_pstr = jenv->GetIntArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    /* possible thread/reentrant code problem */
    static QVariantList $1_str;
    if ($1_len) {
        $1_str.clear();
        $1_str.reserve($1_len);
        for (jsize i = 0; i < $1_len; ++i) {
            $1_str.push_back($1_pstr[i]);
        }
    }
    $result = &$1_str;
    jenv->ReleaseIntArrayElements($input, $1_pstr, 0);
%}

%typemap(javain) const QVariantList& "$javainput"

%typemap(javaout) const QVariantList&
{
    return $jnicall;
}


/* DO NOT EDIT THIS FILE - it is machine generated */
#include <native.h>
/* Header for class java_lang_ClassLoader */

#ifndef _Included_java_lang_ClassLoader
#define _Included_java_lang_ClassLoader
struct Hjava_lang_ClassLoader;
struct Hjava_util_Hashtable;
struct Hjava_security_cert_Certificate;
struct Hjava_util_Vector;
struct Hjava_util_Set;
struct Hjava_util_HashMap;
struct Hjava_security_ProtectionDomain;

typedef struct Classjava_lang_ClassLoader {
    /*boolean*/ int32_t initialized;
    struct Hjava_lang_ClassLoader *parent;
    struct Hjava_util_Hashtable *package2certs;
    struct HArrayOfObject *nocerts;
    struct Hjava_util_Vector *classes;
    struct Hjava_util_Set *domains;
    struct Hjava_util_HashMap *packages;
/* Inaccessible static: bootstrapClassPath */
/* Inaccessible static: scl */
/* Inaccessible static: sclSet */
/* Inaccessible static: getClassLoaderPerm */
    struct Hjava_security_ProtectionDomain *defaultDomain;
/* Inaccessible static: defaultPermissions */
/* Inaccessible static: loadedLibraryNames */
/* Inaccessible static: systemNativeLibraries */
    struct Hjava_util_Vector *nativeLibraries;
/* Inaccessible static: nativeLibraryContext */
/* Inaccessible static: usr_paths */
/* Inaccessible static: sys_paths */
/* Inaccessible static: class_00024java_00024lang_00024ClassLoader */
} Classjava_lang_ClassLoader;
HandleTo(java_lang_ClassLoader);

#ifdef __cplusplus
extern "C" {
#endif
struct Hjava_lang_String;
struct Hjava_lang_Class;
extern struct Hjava_lang_Class *java_lang_ClassLoader_defineClass0(struct Hjava_lang_ClassLoader *,struct Hjava_lang_String *,HArrayOfByte *,int32_t,int32_t,struct Hjava_security_ProtectionDomain *);
extern void java_lang_ClassLoader_resolveClass0(struct Hjava_lang_ClassLoader *,struct Hjava_lang_Class *);
extern struct Hjava_lang_Class *java_lang_ClassLoader_findBootstrapClass(struct Hjava_lang_ClassLoader *,struct Hjava_lang_String *);
extern struct Hjava_lang_Class *java_lang_ClassLoader_findLoadedClass(struct Hjava_lang_ClassLoader *,struct Hjava_lang_String *);
extern struct Hjava_lang_ClassLoader *java_lang_ClassLoader_getCallerClassLoader(struct Hjava_lang_ClassLoader *);
#ifdef __cplusplus
}
#endif
#endif

#
# soletta.bb
#

DESCRIPTION = "Soletta library and modules"
SECTION = "examples"
DEPENDS = "glib-2.0 libpcre pkgconfig python3-jsonschema-native icu curl nodejs libmicrohttpd"
LICENSE = "BSD"
LIC_FILES_CHKSUM = "file://COPYING;md5=53eeaddf328b23e2355816e257450eaa"
PV = "1_beta15+git${SRCPV}"

SRC_URI = "gitsm://github.com/solettaproject/soletta.git;protocol=git \
           file://0001-export-vars-for-nodegyp.patch \
"
SRCREV = "2bbe44194e563c4f1d6049ce4d5094760d25b21e"

S = "${WORKDIR}/git"

inherit cml1 python3native

PACKAGES = " \
         ${PN}-staticdev \
         ${PN}-dev \
         ${PN}-dbg \
         ${PN} \
"

FILES_${PN}-staticdev = " \
                      ${libdir}/libsoletta.a \
"

FILES_${PN}-dbg = " \
                 ${datadir}/gdb \
"

FILES_${PN}-dev = " \
                ${datadir}/soletta/* \
                ${includedir}/soletta/* \
                ${libdir}/pkgconfig/soletta.pc \
                ${libdir}/soletta/modules/flow/* \
                ${libdir}/soletta/modules/pin-mux/* \
                ${libdir}/soletta/modules/linux-micro/* \
                ${libdir}/soletta/modules/flow-metatype/* \
"

FILES_${PN} = " \
            ${bindir}/sol* \
            ${libdir}/libsoletta.so* \
            ${libdir}/soletta/soletta-image-hash \
            ${libdir}/node_modules/soletta-node/ \
"

# Setup what PACKAGES should be installed by default.
# If a package should not being installed, use BAD_RECOMMENDS.
RRECOMMENDS_${PN} = "\
                  ${PN} \
                  ${PN}-dev \
"

# since we only enable flow-module-udev only with systemd feature
# can can disable the RDEPENDS based on the same criteria
RDEPENDS_${PN} = " \
             ${@bb.utils.contains('DISTRO_FEATURES','systemd','libudev','',d)} \
             chrpath \
             libpcre \
"

# do_package_qa tells soletta rdepends on soletta-dev
# maybe an non-obvious implicit rule implied by yocto
INSANE_SKIP_${PN} += "dev-deps file-rdeps"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_DEFAULT_DEPS = "1"

B = "${WORKDIR}/git"

do_configure_prepend() {
   export TARGETCC="${CC}"
   export TARGETAR="${AR}"
}

do_compile() {

    # changing the home directory to the working directory, the .npmrc will be created in this directory
    export HOME=${WORKDIR}

    # does not build dev packages
    npm config set dev false

    # access npm registry using http
    npm set strict-ssl false
    npm config set registry http://registry.npmjs.org/

    # configure http proxy if neccessary
    if [ -n "${http_proxy}" ]; then
        npm config set proxy ${http_proxy}
        NODE_GYP_PROXY="--proxy=${http_proxy}"
    fi
    if [ -n "${HTTP_PROXY}" ]; then
        npm config set proxy ${HTTP_PROXY}
        NODE_GYP_PROXY="--proxy=${HTTP_PROXY}"
    fi

    # configure cache to be in working directory
    npm set cache ${WORKDIR}/npm_cache

    # clear local cache prior to each compile
    npm cache clear

    case ${TARGET_ARCH} in
        i?86) targetArch="ia32"
            echo "targetArch = 32"
            ;;
        x86_64) targetArch="x64"
            echo "targetArch = 64"
            ;;
        arm) targetArch="arm"
            ;;
        mips) targetArch="mips"
            ;;
        sparc) targetArch="sparc"
            ;;
        *) echo "unknown architecture"
           exit 1
            ;;
    esac

    # Export needed variables to build Soletta's JS bindings
    export USE_NODEJS=1
    export NODE_GYP_PATH=${STAGING_DIR_TARGET}/${libdir}/node_modules/npm/bin/node-gyp-bin
    export NODE_GYP_FLAGS="--arch=${targetArch} ${NODE_GYP_PROXY}"

    oe_runmake CFLAGS="--sysroot=${STAGING_DIR_TARGET} -pthread -lpcre" TARGETCC="${CC}" TARGETAR="${AR}"
}

do_install() {
   oe_runmake DESTDIR=${WORKDIR}/image install CFLAGS="--sysroot=${STAGING_DIR_TARGET}" TARGETCC="${CC}" TARGETAR="${AR}"
   unlink ${WORKDIR}/image/usr/lib/libsoletta.so
   mv ${WORKDIR}/image/usr/lib/libsoletta.so.0.0.1 ${WORKDIR}/image/usr/lib/libsoletta.so
   ln -sf libsoletta.so ${WORKDIR}/image/usr/lib/libsoletta.so.0.0.1
   COMMIT_ID=`git --git-dir=${WORKDIR}/git/.git rev-parse --verify HEAD`
   echo "Soletta: $COMMIT_ID" > ${D}/usr/lib/soletta/soletta-image-hash

   # Install Soletta node bindings
   install -d ${D}${libdir}/node_modules/soletta-node/
   install -m 0644 ${S}/index.js ${D}${libdir}/node_modules/soletta-node/index.js
   install -m 0644 ${S}/lowlevel.js ${D}${libdir}/node_modules/soletta-node/lowlevel.js
   install -m 0644 ${S}/package.json ${D}${libdir}/node_modules/soletta-node/package.json

   cp -r ${S}/node_modules/ ${D}${libdir}/node_modules/soletta-node/
   install -d ${D}${libdir}/node_modules/soletta-node/build/Release/
   install -m 0755 ${S}/build/Release/soletta.node ${D}${libdir}/node_modules/soletta-node/build/Release/
}

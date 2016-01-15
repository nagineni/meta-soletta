#
# soletta.bb
#

DESCRIPTION = "Soletta library and modules"
SECTION = "examples"
DEPENDS = "glib-2.0 libpcre pkgconfig python3-jsonschema-native icu curl nodejs"
LICENSE = "BSD"
LIC_FILES_CHKSUM = "file://COPYING;md5=53eeaddf328b23e2355816e257450eaa"
PV = "1_beta11"

#SRC_URI = "gitsm://github.com/solettaproject/soletta.git;protocol=git;tag=v${PV}"
SRC_URI = "git://git@github.com/nagineni/soletta.git;protocol=https;branch=hw_bindings"
SRCREV = "bb89e311a98ee79061ce37a5e2042206f45a35b1"

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
   oe_runmake CFLAGS="--sysroot=${STAGING_DIR_TARGET} -pthread -lpcre" TARGETCC="${CC}" TARGETAR="${AR}"

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
    fi
    if [ -n "${HTTP_PROXY}" ]; then
        npm config set proxy ${HTTP_PROXY}
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

    # Install dependencies without building the package
    npm --arch=${targetArch} --production --verbose install --ignore-scripts

    # configure http proxy if neccessary
    export SOLETTA_FROM_MAKE="true"
    if [ -n "${http_proxy}" ]; then
        ${STAGING_DIR_TARGET}/${libdir}/node_modules/npm/bin/node-gyp-bin/node-gyp --arch=${targetArch} --proxy=${http_proxy} configure
    elif [ -n "${HTTP_PROXY}" ]; then
        ${STAGING_DIR_TARGET}/${libdir}/node_modules/npm/bin/node-gyp-bin/node-gyp --arch=${targetArch} --proxy=${HTTP_PROXY} configure
    else
        ${STAGING_DIR_TARGET}/${libdir}/node_modules/npm/bin/node-gyp-bin/node-gyp --arch=${targetArch} configure
    fi

    ${STAGING_DIR_TARGET}/${libdir}/node_modules/npm/bin/node-gyp-bin/node-gyp --arch=${targetArch} build
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

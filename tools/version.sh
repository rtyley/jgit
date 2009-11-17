#!/bin/sh

# Update all pom.xml and MANIFEST.MF with new build number
#
# TODO(spearce) This should be converted to some sort of
# Java based Maven plugin so its fully portable.
#

case "$1" in
--snapshot=*)
	V=$(echo "$1" | perl -pe 's/^--snapshot=//')
	if [ -z "$V" ]
	then
		echo >&2 "usage: $0 --snapshot=0.n.0"
		exit 1
	fi
	case "$V" in
	*-SNAPSHOT) : ;;
	*) V=$V-SNAPSHOT ;;
	esac
	;;

--release)
	V=$(git describe HEAD) || exit
	;;

*)
	echo >&2 "usage: $0 {--snapshot=0.n.0 | --release}"
	exit 1
esac

case "$V" in
v*) V=$(echo "$V" | perl -pe s/^v//) ;;
esac

case "$V" in
*-SNAPSHOT)
	POM_V=$V
	MF_V=$(echo "$V" | perl -pe 's/-SNAPSHOT$/.qualifier/')
	;;
*-[1-9]*-g[0-9a-f]*)
	POM_V=$(echo "$V" | perl -pe 's/-(\d+-g.*)$/.$1/')
	MF_V=$POM_V
	;;
*)
	POM_V=$V
	MF_V=$V
	;;
esac

perl -pi -e '
	s/^(Bundle-Version:).*/$1 '"$MF_V"'/
	' $(git ls-files | grep META-INF/MANIFEST.MF)

perl -pi -e '
	if ($ARGV ne $old_argv) {
		$seen_version = 0;
		$old_argv = $ARGV;
	}
	if (!$seen_version) {
		$seen_version = 1 if
		s{(<version>).*(</version>)}{${1}'"$POM_V"'${2}};
	}
	' $(git ls-files | grep pom.xml)

git diff

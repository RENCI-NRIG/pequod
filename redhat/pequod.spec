Summary: Pequod - The ORCA IAAS Distributed Command Shell
Name: pequod
Version: 4.0
Release: 1
BuildArch: noarch
BuildRoot: %{_builddir}/%{name}-root
Source: %{name}-%{version}.tgz
Group: Applications/Communications
Vendor: ExoGENI
Packager: ExoGENI
License: GENI Public License
URL: https://geni-orca.renci.org/svn/orca-external/pequod/trunk

BuildRequires:  jdk
Requires:       jdk

%define homedir /opt/pequod
# couldn't find another way to disable the brp-java-repack-jars which was called in __os_install_post
%define __os_install_post %{nil}
# And this is needed to get around the generated wrapper binaries...
%global debug_package %{nil}

%description
Pequod is the distributed command shell used to manage instances of
the ORCA infrastructure-as-a-service control framework.

%prep
%setup

%build
LANG=en_US.UTF-8 PATH=/usr/java/latest/bin:$PATH mvn clean package

%install
# Prep the install location.
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{homedir}
# Copy over the generated utilities and dependencies
cp -R target/appassembler/bin $RPM_BUILD_ROOT%{homedir}
cp -R target/appassembler/repo $RPM_BUILD_ROOT%{homedir}
# Ensure the all utilities are executable.
chmod 755 $RPM_BUILD_ROOT%{homedir}/bin/*

# Clean up the bin and lib directories
rm -rf $RPM_BUILD_ROOT%{homedir}/bin/*.bat

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-, root, root)
%{homedir}/bin
%{homedir}/repo

%changelog
*Fri May 16 2014 Victor J. Orlikowski <vjo@cs.duke.edu>
- Initial revision for Pequod source tree

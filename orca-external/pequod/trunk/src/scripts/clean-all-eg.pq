# clean up active reservations on all AMs
# this way even if SMs somehow lost slice info, AMs will clean up VMs, 
# vlans and network connections as best they can

set current actors rci-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors rci-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors fiu-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors fiu-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors uh-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors uh-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

# DISABLE ALL SHADOW DOMAINS - THEY ARE NO LONGER IN USE.
#set current actors fake-nersc-site
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current

#set current actors nersc-net-site
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current

#set current actors fake-osg-site
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current

#set current actors osg-net-site
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current

set current actors esnet-site
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors nlr-net-site
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors ion-net-site
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors ben-network-site
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors duke-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors duke-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

# DISABLE UNC FOR NOW
# set current actors unc-net-am
# show reservations for all actor current state active
# set current reservations
# manage close reservation current actor current

# set current actors unc-vm-am
# show reservations for all actor current state active
# set current reservations
# manage close reservation current actor current

set current actors nicta-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors nicta-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors bbn-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors bbn-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors uvanl-net-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

set current actors uvanl-vm-am
show reservations for all actor current state active
set current reservations
manage close reservation current actor current

#set current actors ncsu-net-am
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current

#set current actors ncsu-vm-am
#show reservations for all actor current state active
#set current reservations
#manage close reservation current actor current


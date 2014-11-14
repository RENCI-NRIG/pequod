# Exogeni claims script
show containers

# RCI
manage claim reservation all actor ndl-broker from rci-vm-am
manage claim reservation all actor ndl-broker from rci-net-am

# BBN
manage claim reservation all actor ndl-broker from bbn-vm-am
manage claim reservation all actor ndl-broker from bbn-net-am

# Duke
# DISABLED UNTIL GPO "RECOGNIZES" DUKE RACK
#manage claim reservation all actor ndl-broker from duke-vm-am
#manage claim reservation all actor ndl-broker from duke-net-am

# NICTA
manage claim reservation all actor ndl-broker from nicta-vm-am
manage claim reservation all actor ndl-broker from nicta-net-am

# UVA-NL
manage claim reservation all actor ndl-broker from uvanl-vm-am
# DISABLED UNTIL VLANS ARE OUT OF MAINTENANCE
#manage claim reservation all actor ndl-broker from uvanl-net-am

# FIU
manage claim reservation all actor ndl-broker from fiu-vm-am
manage claim reservation all actor ndl-broker from fiu-net-am

# UH
manage claim reservation all actor ndl-broker from uh-vm-am
manage claim reservation all actor ndl-broker from uh-net-am

# BEN
manage claim reservation all actor ndl-broker from ben-network-site

# NLR
manage claim reservation all actor ndl-broker from nlr-net-site

# ION
manage claim reservation all actor ndl-broker from ion-net-site

# Inventory on broker
show inventory for ndl-broker

# save the result
set current slices

# display inventory
show reservations for current actor ndl-broker


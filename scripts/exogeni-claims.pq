# Exogeni claims script

show containers

# RCI
manage claim reservation all actor ndl-broker from rci-vm-am
manage claim reservation all actor ndl-broker from rci-net-am
# BBN
manage claim reservation all actor ndl-broker from bbn-vm-am
manage claim reservation all actor ndl-broker from bbn-net-am
# Duke
manage claim reservation all actor ndl-broker from duke-vm-am
manage claim reservation all actor ndl-broker from duke-net-am
# NICTA
manage claim reservation all actor ndl-broker from nicta-vm-am
manage claim reservation all actor ndl-broker from nicta-net-am
# FIU
manage claim reservation all actor ndl-broker from fiu-vm-am
manage claim reservation all actor ndl-broker from fiu-net-am

# Inventory on broker
show inventory for ndl-broker
# save the result
set current slices
# display inventory
show reservations for current actor ndl-broker


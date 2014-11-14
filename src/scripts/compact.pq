# this script compacts the database by removing all dead slices and their reservations

show deadslices for rci-sm
set current slices
show reservations for current actor rci-sm
set current reservations
manage remove slice current actor rci-sm
manage remove reservation current actor rci-sm


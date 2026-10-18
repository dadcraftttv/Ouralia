# Desktop build of the dcs library: build/dcs.pd_linux, loaded by [declare -lib dcs] with build/ on Pd's search path.
# PD_SRC = Pd's source (for m_pd.h); by default the libpd the tests and the desktop app use (tools/build_libpd.sh).
# The Android build is tools/build_dcs_android.sh.
PD_SRC ?= ../tools/.libpd/src/pure-data/src
SRC = $(wildcard src/*.c)
# the effect ports (they include dcs_fx.h) match Pd's objects only with exact float arithmetic: no -ffast-math
FX_SRC = $(shell grep -l '"dcs_fx.h"' src/*.c)
CFLAGS ?= -O3 -ffast-math -fPIC -Wall -Wno-unused-parameter

build/dcs.pd_linux: $(SRC) $(wildcard src/*.h) $(PD_SRC)/m_pd.h
	mkdir -p build
	mkdir -p build/obj
	for f in $(filter-out $(FX_SRC),$(SRC)); do $(CC) $(CFLAGS) -DPD -I$(PD_SRC) -c $$f -o build/obj/$$(basename $$f .c).o || exit 1; done
	for f in $(FX_SRC); do $(CC) $(CFLAGS) -fno-fast-math -DPD -I$(PD_SRC) -c $$f -o build/obj/$$(basename $$f .c).o || exit 1; done
	$(CC) -shared -o $@ $(patsubst src/%.c,build/obj/%.o,$(SRC)) -lm

clean:
	rm -rf build
.PHONY: clean

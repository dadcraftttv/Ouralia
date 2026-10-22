# SPDX-License-Identifier: GPL-3.0-or-later
"""The Patch builder (named nodes to a .pd file) and the small helpers every part uses."""
from .constants import *  # noqa: F401,F403 (the tuning numbers)


class Patch:
    def __init__(self):
        self.lines, self.index, self.conns = [], {}, []
        self.groups = []

    def _add(self, name, line):
        assert name not in self.index, "duplicate node " + name
        self.index[name] = len(self.lines)
        self.lines.append(line)
        return name

    def obj(self, name, text, x, y):
        return self._add(name, f"#X obj {x} {y} {text};")

    def msg(self, name, text, x, y):
        return self._add(name, f"#X msg {x} {y} {text};")

    def c(self, a, b, outlet=0, inlet=0):
        self.conns.append((a, outlet, b, inlet))

    def sig2(self, name, op, a, b, x, y):
        """Binary signal op: a -> left inlet, b -> right inlet."""
        self.obj(name, op, x, y)
        self.c(a, name, 0, 0)
        self.c(b, name, 0, 1)
        return name

    # ---- switchable blocks ----
    # A block's objects go into a subpatch with [switch~], so its DSP costs nothing while it can't be heard
    # (Pd runs every signal object all the time otherwise: measured on the phone, the patch took about half
    # a core). Membership is by node name; connections that cross the boundary get inlets/outlets added
    # automatically, signal or control after the source object. A switched-off subpatch outputs silence and
    # its delay lines come back empty, so nothing stale is heard when it switches on again.
    # self.groups: (group name, compiled regex); first match wins

    # objects whose name ends in ~ but whose outlets carry messages
    CONTROL_TILDE = {"snapshot~", "env~", "samplerate~", "threshold~", "bonk~", "fiddle~", "sigmund~", "dcs_pitch~", "dcs_onset~"}

    def group(self, gname, pattern):
        self.groups.append((gname, re.compile(pattern)))

    def _group_of(self, name):
        if name.startswith("grp_"):
            return name.split("_")[1]
        for g, rx in self.groups:
            if rx.match(name):
                return g
        return None

    def _is_signal(self, name, outlet):
        words = self.lines[self.index[name]].rstrip(";").split()
        if words[1] != "obj" or len(words) < 5:
            return False
        cls = words[4]
        return cls.endswith("~") and cls not in self.CONTROL_TILDE

    def text(self):
        names = sorted(self.index, key=self.index.get)
        member = {n: self._group_of(n) for n in names}
        used = sorted({g for g in member.values() if g}, key=lambda g: min(self.index[n] for n in names if member[n] == g))
        # inlets/outlets per group: one per (source node, outlet)
        ins = {g: [] for g in used}
        outs = {g: [] for g in used}
        for a, o, b, i in self.conns:
            ga, gb = member[a], member[b]
            if ga != gb:
                if ga and (a, o) not in outs[ga]:
                    outs[ga].append((a, o))
                if gb and (a, o) not in ins[gb]:
                    ins[gb].append((a, o))
        # parent indices: top-level objects in order, each group at its first member's position
        parent, local, seen = {}, {}, set()
        order = []
        for n in names:
            g = member[n]
            if g is None:
                parent[n] = len(order); order.append(("obj", n))
            elif g not in seen:
                seen.add(g); parent["grp:" + g] = len(order); order.append(("grp", g))
        out = ["#N canvas 0 0 900 900 12;", "#X declare -lib dcs;"]   # our own objects (dcs/), before any is made
        for kind, x in order:
            if kind == "obj":
                out.append(self.lines[self.index[x]])
                continue
            g = x
            mem = [n for n in names if member[n] == g]
            body = [self.lines[self.index[n]] for n in mem]
            for k, n in enumerate(mem):
                local[(g, n)] = k
            for k, (a, o) in enumerate(ins[g]):
                local[(g, "in", a, o)] = len(body)
                body.append(f"#X obj {10 + 80 * k} 0 {'inlet~' if self._is_signal(a, o) else 'inlet'};")
            for k, (a, o) in enumerate(outs[g]):
                local[(g, "out", a, o)] = len(body)
                body.append(f"#X obj {10 + 80 * k} 2000 {'outlet~' if self._is_signal(a, o) else 'outlet'};")
            conns = []
            for a, o, b, i in self.conns:
                ga, gb = member[a], member[b]
                src = local[(g, a)] if ga == g else (local[(g, "in", a, o)] if gb == g else None)
                if ga == g and gb == g:
                    conns.append(f"#X connect {local[(g, a)]} {o} {local[(g, b)]} {i};")
                elif gb == g:
                    conns.append(f"#X connect {local[(g, 'in', a, o)]} 0 {local[(g, b)]} {i};")
                elif ga == g:
                    key = (g, "out", a, o)
                    line = f"#X connect {local[(g, a)]} {o} {local[key]} 0;"
                    if line not in conns:
                        conns.append(line)
            out.append(f"#N canvas 0 0 900 900 {g} 0;")
            out += body + conns
            out.append(f"#X restore {200 + 100 * used.index(g)} 2200 pd {g};")
        made = set()
        for a, o, b, i in self.conns:
            ga, gb = member[a], member[b]
            if ga == gb is None:
                out.append(f"#X connect {parent[a]} {o} {parent[b]} {i};")
                continue
            if ga == gb:
                continue
            if ga:   # leaves group ga through its outlet for (a, o)
                src, so = parent["grp:" + ga], outs[ga].index((a, o))
            else:
                src, so = parent[a], o
            if gb:
                dst, di = parent["grp:" + gb], ins[gb].index((a, o))
            else:
                dst, di = parent[b], i
            line = f"#X connect {src} {so} {dst} {di};"
            if line not in made:   # one cable per inlet; the subpatch fans out inside
                made.add(line)
                out.append(line)
        return "\n".join(out) + "\n"

def add_switch(P, g, factors, tail_ms, also=(), block="", dsp=True):
    """Switches block g's DSP on while the product of the receivers `factors` is above 0, and off again
    3 x smoothing + tail_ms after it drops to 0 (so fades and ring-outs finish first). `also` are nodes
    that get the same 0/1 (e.g. a metro that only feeds the block)."""
    x, y = 4600 + 160 * len(P.groups), 1600
    n = f"gate_{g}"
    terms = "*".join(f"$f{k + 1}" for k in range(len(factors)))
    P.obj(f"{n}_calc", ("expr (" + terms + ")>0").replace("$", BS + "$"), x, y + 120)
    P.obj(f"{n}_s0", "f", x, y + 90)
    P.c(f"{n}_s0", f"{n}_calc")
    for k, rcv in enumerate(factors):
        P.obj(f"{n}_r{k}", f"r {rcv}", x + 40 * k, y)
        P.obj(f"{n}_t{k}", "t b f", x + 40 * k, y + 30)
        P.c(f"{n}_r{k}", f"{n}_t{k}")
        if k == 0:
            P.c(f"{n}_t{k}", f"{n}_s0", 1, 1)
        else:
            P.c(f"{n}_t{k}", f"{n}_calc", 1, k)
        P.c(f"{n}_t{k}", f"{n}_s0", 0, 0)
    P.obj(f"{n}_chg", "change -1", x, y + 150)                  # the first answer always goes out
    P.obj(f"{n}_sel", "sel 0", x, y + 180)
    P.obj(f"{n}_del", "del 1000", x, y + 240)
    P.obj(f"{n}_on", "t b b", x + 60, y + 210)
    P.msg(f"{n}_stop", "stop", x + 60, y + 240)
    P.msg(f"{n}_one", "1", x + 110, y + 240)
    P.msg(f"{n}_zero", "0", x, y + 270)
    P.obj(f"{n}_s", f"s sw_{g}", x, y + 300)
    P.c(f"{n}_calc", f"{n}_chg"); P.c(f"{n}_chg", f"{n}_sel")
    P.c(f"{n}_sel", f"{n}_del", 0, 0); P.c(f"{n}_del", f"{n}_zero"); P.c(f"{n}_zero", f"{n}_s")
    P.c(f"{n}_sel", f"{n}_on", 1, 0); P.c(f"{n}_on", f"{n}_stop", 1, 0); P.c(f"{n}_stop", f"{n}_del")
    P.c(f"{n}_on", f"{n}_one", 0, 0); P.c(f"{n}_one", f"{n}_s")
    P.obj(f"{n}_wait", ("expr 3*$f1+" + str(tail_ms)).replace("$", BS + "$"), x + 120, y + 180)
    P.c("r_smooth", f"{n}_wait"); P.c(f"{n}_wait", f"{n}_del", 0, 1)
    if not dsp:      # (a dcs object hears "sw_<g>" itself)
        return
    # inside the block
    P.obj(f"grp_{g}_r", f"r sw_{g}", 10, 10)
    P.obj(f"grp_{g}_sw", ("switch~ " + block).strip(), 10, 40)
    P.c(f"grp_{g}_r", f"grp_{g}_sw")
    for node in also:
        P.c(f"grp_{g}_r", node)

def soft_limit(P, n, src, limit, gain=1.0, x=0, y=0):
    """limit x tanh(src x gain / limit), from plain objects (an [expr~] costs several times more): the rational
    curve u(27+u^2)/(27+9u^2), within ~2% of tanh, reaching 1 at u = 3 (clipped there). Returns the output."""
    P.obj(f"{n}_u", f"*~ {gain / limit:.6g}", x, y); P.c(src, f"{n}_u")
    P.obj(f"{n}_uc", "clip~ -3 3", x, y + 15); P.c(f"{n}_u", f"{n}_uc")
    P.sig2(f"{n}_u2", "*~", f"{n}_uc", f"{n}_uc", x + 60, y + 30)
    P.obj(f"{n}_nu", "+~ 27", x, y + 45); P.c(f"{n}_u2", f"{n}_nu")
    P.sig2(f"{n}_num", "*~", f"{n}_uc", f"{n}_nu", x, y + 60)
    P.obj(f"{n}_d9", "*~ 9", x + 60, y + 45); P.c(f"{n}_u2", f"{n}_d9")
    P.obj(f"{n}_den", "+~ 27", x + 60, y + 60); P.c(f"{n}_d9", f"{n}_den")
    P.sig2(f"{n}_q", "/~", f"{n}_num", f"{n}_den", x, y + 75)
    P.obj(f"{n}", f"*~ {limit}", x, y + 90); P.c(f"{n}_q", f"{n}")
    return n

def fx(P, name, text, src, *params, x=0, y=0, smooth="r_smooth"):
    """A dcs effect object `name` (`text`) fed by the signal `src`; each (receiver, selector) pair becomes
    [r receiver] -> [selector $1( into it, and the smoothing time arrives as "smooth" (from `smooth`)."""
    P.obj(name, text, x, y)
    P.c(src, name)
    for k, (recv, sel) in enumerate(params):
        P.obj(f"{name}_r{k}", f"r {recv}", x + 70 * k, y - 40)
        P.msg(f"{name}_m{k}", f"{sel} {BS}$1", x + 70 * k, y - 20)
        P.c(f"{name}_r{k}", f"{name}_m{k}"); P.c(f"{name}_m{k}", name)
    if smooth:
        P.msg(f"{name}_ms", f"smooth {BS}$1", x + 70 * len(params), y - 20)
        P.c(smooth, f"{name}_ms"); P.c(f"{name}_ms", name)
    return name

def smoothed(P, key, recv, x, y, ramp=30, mult=None):
    """[r recv] -> ramped signal. The ramp time follows the global 'smooth' parameter."""
    P.obj(f"r_{key}", f"r {recv}", x, y)
    P.obj(f"pk_{key}", f"pack f {ramp}", x, y + 30)
    P.obj(f"ln_{key}", "line~", x, y + 60)
    P.c(f"r_{key}", f"pk_{key}")
    P.c(f"pk_{key}", f"ln_{key}")
    P.c("smooth_x3" if mult == 3 else "r_smooth", f"pk_{key}", 0, 1)
    return f"ln_{key}"

def handling_gate(P, n, src, x, y, outlet=0, wait=True):
    """Passes a sudden-sound bang from src on only if it isn't handling noise: no jolt in the last HND_JOLT_MS and
    handling under HND_TRIG. With wait, it checks HND_CHECK_MS later (a jolt reported a little after the mic heard
    it still counts); chimes don't wait (an answer to a clap must be quick, and the mic's own delay usually lets the
    jolt arrive first). Returns the gate's output (a bang)."""
    P.obj(f"{n}_del", f"del {HND_CHECK_MS}" if wait else "t b", x, y); P.c(src, f"{n}_del", outlet, 0)
    P.obj(f"{n}_ok", f"expr (hjolt==0)&&(hlev<{HND_TRIG})", x, y + 15); P.c(f"{n}_del", f"{n}_ok")
    P.obj(f"{n}_sel", "sel 1", x, y + 30); P.c(f"{n}_ok", f"{n}_sel")
    return f"{n}_sel"

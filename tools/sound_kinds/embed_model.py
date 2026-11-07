#!/usr/bin/env python3
"""Accuracy step 4a (Nov 6; DadCraft: "lets look into accuracy step 4"): YAMNet with its embedding as a second output.

The app's model (and the bench's) gives only the 521 class scores. The 1024-value embedding they come from is
tensor `layer28/reduce_mean` (int8), just before the last layer; this adds it to the subgraph's outputs, so a small
trained layer can read it with no extra work on the phone. The scores stay identical (checked here on noise and a
tone). The label list zipped onto the end of the original file is copied over, so the result reads like the original.

    tools/.venv/bin/python tools/sound_kinds/embed_model.py IN.tflite OUT.tflite
Plan: docs/design/sound-kinds.md "Accuracy step 4 plan".
"""
import io, sys, zipfile
import flatbuffers
import numpy as np
from ai_edge_litert import schema_py_generated as sch
from ai_edge_litert.interpreter import Interpreter

EMBEDDING = "layer28/reduce_mean"


def add_output(src, dst):
    buf = open(src, "rb").read()
    m = sch.ModelT.InitFromObj(sch.Model.GetRootAsModel(buf, 0))
    g = m.subgraphs[0]
    found = [i for i, t in enumerate(g.tensors) if t.name.decode().endswith(EMBEDDING)]
    if len(found) != 1:
        sys.exit(f"expected one {EMBEDDING} tensor, found {len(found)}")
    if found[0] in list(g.outputs):
        sys.exit("the embedding is already an output")
    g.outputs = list(g.outputs) + found
    b = flatbuffers.Builder(1 << 20)
    b.Finish(m.Pack(b), file_identifier=b"TFL3")
    out = bytes(b.Output())
    try:   # (the labels zipped onto the original: a zip reads fine after other bytes)
        z = zipfile.ZipFile(io.BytesIO(buf))
        extra = io.BytesIO()
        with zipfile.ZipFile(extra, "w") as w:
            for n in z.namelist():
                w.writestr(n, z.read(n))
        out += extra.getvalue()
    except zipfile.BadZipFile:
        pass
    open(dst, "wb").write(out)


def check(src, dst):
    a, e = Interpreter(model_path=src), Interpreter(model_path=dst)
    a.allocate_tensors()
    e.allocate_tensors()
    t = np.arange(15600) / 16000
    for x in (np.random.default_rng(1).standard_normal(15600) * 0.1, 0.3 * np.sin(2 * np.pi * 440 * t)):
        x = x.astype(np.float32)
        for i in (a, e):
            i.set_tensor(i.get_input_details()[0]["index"], x)
            i.invoke()
        s0 = a.get_tensor(a.get_output_details()[0]["index"])
        od = e.get_output_details()
        if len(od) != 2 or not np.array_equal(s0, e.get_tensor(od[0]["index"])):
            sys.exit("FAIL: the scores changed")
        if list(od[1]["shape"])[-1] != 1024 or not np.count_nonzero(e.get_tensor(od[1]["index"])):
            sys.exit("FAIL: no embedding")
    scale, zero = od[1]["quantization"]
    print(f"OK: scores identical, embedding 1024 int8 (scale {scale:.6g}, zero point {zero})")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    add_output(sys.argv[1], sys.argv[2])
    check(sys.argv[1], sys.argv[2])

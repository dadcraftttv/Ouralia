/* dcs_wav.h: reads a WAV file (PCM 8/16/24/32-bit or 32/64-bit float, plain or extensible) into memory as mono
 * floats at the file's own rate (the channels averaged). For kits' samples: small files, read once.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#ifndef DCS_WAV_H
#define DCS_WAV_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static unsigned int dcs_wav_u32(const unsigned char *p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((unsigned int)p[3] << 24); }
static unsigned int dcs_wav_u16(const unsigned char *p) { return p[0] | (p[1] << 8); }

/* Returns the samples (free() them) and sets *len and *sr; 0 on failure, with *why set. At most max_frames. */
static float *dcs_wav_read(const char *path, long max_frames, long *len, double *sr, const char **why)
{
    FILE *f = fopen(path, "rb");
    unsigned char h[12], ck[8], fmt[40];
    int fmtok = 0, format = 0, chans = 0, bits = 0;
    float *out = 0;
    *len = 0; *sr = 0; *why = "not a WAV file";
    if (!f) { *why = "cannot open it"; return 0; }
    if (fread(h, 1, 12, f) != 12 || memcmp(h, "RIFF", 4) || memcmp(h + 8, "WAVE", 4)) goto done;
    while (fread(ck, 1, 8, f) == 8)
    {
        unsigned int size = dcs_wav_u32(ck + 4);
        if (!memcmp(ck, "fmt ", 4))
        {
            unsigned int n = size < sizeof(fmt) ? size : sizeof(fmt);
            if (n < 16 || fread(fmt, 1, n, f) != n) goto done;
            if (size > n) fseek(f, (long)(size - n + (size & 1)), SEEK_CUR);
            else if (size & 1) fseek(f, 1, SEEK_CUR);
            format = dcs_wav_u16(fmt); chans = dcs_wav_u16(fmt + 2); *sr = dcs_wav_u32(fmt + 4); bits = dcs_wav_u16(fmt + 14);
            if (format == 0xFFFE && n >= 26) format = dcs_wav_u16(fmt + 24);      /* extensible: the sub-format */
            fmtok = 1;
        }
        else if (!memcmp(ck, "data", 4))
        {
            int bps = bits / 8, frame = bps * chans;
            long frames, i;
            unsigned char *raw;
            if (!fmtok) goto done;
            if (!(format == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32))
                && !(format == 3 && (bits == 32 || bits == 64)))
            { *why = "an unsupported WAV encoding (use PCM or float)"; goto done; }
            if (chans < 1 || *sr < 1000) goto done;
            frames = (long)(size / frame);
            if (frames > max_frames) frames = max_frames;
            if (frames < 1) { *why = "it is empty"; goto done; }
            raw = (unsigned char *)malloc((size_t)frames * frame);
            out = (float *)malloc((size_t)frames * sizeof(float));
            if (!raw || !out) { free(raw); free(out); out = 0; *why = "out of memory"; goto done; }
            frames = (long)(fread(raw, 1, (size_t)frames * frame, f) / frame);
            for (i = 0; i < frames; i++)
            {
                double sum = 0;
                for (int c = 0; c < chans; c++)
                {
                    const unsigned char *p = raw + i * frame + c * bps;
                    double v;
                    if (format == 3)
                    {
                        if (bits == 32) { float fv; memcpy(&fv, p, 4); v = fv; }
                        else { double dv; memcpy(&dv, p, 8); v = dv; }
                    }
                    else if (bits == 8) v = (p[0] - 128) / 128.;
                    else if (bits == 16) v = (short)dcs_wav_u16(p) / 32768.;
                    else if (bits == 24)
                    {
                        int iv = p[0] | (p[1] << 8) | (p[2] << 16);
                        if (iv & 0x800000) iv -= 0x1000000;
                        v = iv / 8388608.;
                    }
                    else v = (int)dcs_wav_u32(p) / 2147483648.;
                    sum += v;
                }
                out[i] = (float)(sum / chans);
            }
            free(raw);
            *len = frames;
            *why = 0;
            goto done;
        }
        else
            fseek(f, (long)(size + (size & 1)), SEEK_CUR);
    }
done:
    fclose(f);
    if (*len == 0 && out) { free(out); out = 0; }
    return out;
}

#endif

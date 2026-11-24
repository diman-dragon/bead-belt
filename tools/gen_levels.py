#!/usr/bin/env python3
"""
Генератор таблиц уровней Bead Belt: попытки раскладки (ATTEMPT) и вместимость ленты (BELTS).

Тут две вещи, которые обязаны совпадать с Kotlin: Rng + Levels.layout (Levels.kt) и Planner (Planner.kt).
Менял их — меняй и здесь, затем: python3 tools/gen_levels.py  и вставь напечатанные ATTEMPT и BELTS в Levels.kt.
Проверка совпадения — LevelsTest.layoutsMatchTheGenerator.
"""
import sys
sys.setrecursionlimit(10000)
M32=0xFFFFFFFF
def s32(x):
    x&=M32
    return x-(1<<32) if x&0x80000000 else x
class Rng:
    def __init__(s,seed): s.a=s32(seed)
    def next(s):
        s.a=s32(s.a+0x6D2B79F5)
        a=s.a
        t=s32((a ^ ((a&M32)>>15)) * (1|a))
        t=s32((t + s32((t ^ ((t&M32)>>7)) * (61|t))) ^ t)
        return (((t ^ ((t&M32)>>14)))&M32)/4294967296.0
    def nextInt(s,n): return int(s.next()*n)
BOX=3; SLOTS=4; COLOR_COUNT=8
class Tray:
    __slots__=('c','n','arrow')
    def __init__(s,c,n,arrow=0): s.c=c;s.n=n;s.arrow=arrow
def old_layout(id_,attempt):
    r=Rng(id_*7919+attempt*104729+13)
    hard=id_>100
    k=COLOR_COUNT if hard else min(3+(id_-1)//2,COLOR_COUNT)
    m=(4+(id_-101)//50) if hard else min(2+id_//6,4)
    nCols=3 if id_<3 else 4
    trays=[];boxes=[]
    for c in range(k):
        boxes+= [c]*m
        rem=m*BOX
        while rem>0:
            n=min(rem,3+r.nextInt(4))
            if 1<=rem-n<=2: n=rem
            trays.append(Tray(c,n)); rem-=n
    def shuffle(a):
        for i in range(len(a)-1,0,-1):
            j=r.nextInt(i+1); a[i],a[j]=a[j],a[i]
    shuffle(trays); shuffle(boxes)
    raw=[[] for _ in range(nCols)]
    for i,t in enumerate(trays): raw[i%nCols].append(t)
    pArrow=0.0 if id_<8 else (min(0.25+(id_-100)/400.0,0.5) if hard else min(0.15+id_/400.0,0.35))
    cols=[]
    for ci,col in enumerate(raw):
        nc=[]
        for t in col:
            if r.next()<pArrow:
                opts=[]
                if ci>0: opts.append(1)
                if ci<nCols-1: opts.append(2)
                if ci>0 and ci<nCols-1: opts.append(3)
                nc.append(Tray(t.c,t.n,opts[r.nextInt(len(opts))]))
            else: nc.append(t)
        cols.append(nc)
    elevator=r.nextInt(SLOTS) if (id_>=15 and id_%(2 if hard else 3)==0) else -1
    return cols,boxes,elevator

def stacks_of(boxes):
    st=[[] for _ in range(SLOTS)]
    for i,c in enumerate(boxes): st[i%SLOTS].append(c)
    return st

class Board:
    def __init__(s,cols,boxes,elevator,cap):
        s.cols=cols; s.stacks=stacks_of(boxes); s.elevator=elevator; s.cap=cap
        s.total=len(boxes)
def initial(b):
    s=[[0]*len(b.cols),[0]*SLOTS,[0]*SLOTS,[0]*COLOR_COUNT,b.elevator<0,0,0]  # heads,pos,actN,belt,open,total,done
    settle(b,s); return s
def copy(s): return [s[0][:],s[1][:],s[2][:],s[3][:],s[4],s[5],s[6]]
def settle(b,s):
    heads,pos,act,belt=s[0],s[1],s[2],s[3]
    changed=True
    while changed:
        changed=False
        for i in range(SLOTS-1,-1,-1):
            st=b.stacks[i]
            if pos[i]>=len(st): continue
            if i==b.elevator and not s[4]: continue
            c=st[pos[i]]
            take=min(BOX-act[i],belt[c])
            if take>0:
                belt[c]-=take; s[5]-=take; act[i]+=take; changed=True
            if act[i]==BOX:
                s[6]+=1; s[4]=True; act[i]=0; pos[i]+=1; changed=True
def tap(b,s,col):
    heads=s[0][:]
    if heads[col]>=len(b.cols[col]): return None
    first=b.cols[col][heads[col]]; heads[col]+=1
    batch=[first]
    if first.arrow&1 and col>0 and heads[col-1]<len(b.cols[col-1]):
        batch.append(b.cols[col-1][heads[col-1]]); heads[col-1]+=1
    if first.arrow&2 and col<len(b.cols)-1 and heads[col+1]<len(b.cols[col+1]):
        batch.append(b.cols[col+1][heads[col+1]]); heads[col+1]+=1
    m=copy(s); m[0]=heads
    for t in batch:
        for _ in range(t.n):
            if m[5]>=b.cap:
                settle(b,m)
                if m[5]>=b.cap: return None
            m[3][t.c]+=1; m[5]+=1
            settle(b,m)
    return m
def key(s): return (tuple(s[0]),tuple(s[1]),tuple(s[2]),tuple(s[3]),s[4])
def solve(b,limit=400000):
    seen=set(); nodes=[0]; hit=[False]
    def dfs(s):
        if s[6]==b.total: return True
        nodes[0]+=1
        if nodes[0]>limit: hit[0]=True; return False
        k=key(s)
        if k in seen: return False
        seen.add(k)
        kids=[]
        for col in range(len(b.cols)):
            ns=tap(b,s,col)
            if ns is not None: kids.append((col,ns))
        kids.sort(key=lambda x:(-x[1][6],x[1][5]))
        for col,ns in kids:
            if dfs(ns): return True
            if hit[0]: return False
        return False
    return dfs(initial(b)), nodes[0], hit[0]
def solvable(cols,boxes,el,cap,limit=400000):
    b=Board(cols,boxes,el,cap); return solve(b,limit)[0]
def min_cap(cols,boxes,el,hi=30,limit=400000):
    if not solvable(cols,boxes,el,hi,limit): return None
    lo=4;h=hi
    while lo<h:
        mid=(lo+h)//2
        if solvable(cols,boxes,el,mid,limit): h=mid
        else: lo=mid+1
    return h

# ---------- раскладка (зеркало Levels.layout) ----------
BOTH_FROM=201
def colors_of(i):
    return 3 if i<=2 else 4 if i<=6 else 5 if i<=12 else 6 if i<=22 else 7 if i<=40 else 8
def boxes_per_color(i):
    return 2 if i<=8 else 3 if i<=25 else 4 if i<=80 else 5 if i<=250 else 6
def arrow_chance(i):
    return 0.0 if i<8 else 0.10 if i<=30 else 0.16 if i<=100 else 0.22 if i<=200 else 0.28 if i<=350 else 0.34
def slack(i):
    return 10 if i<=100 else 9 if i<=300 else 8
def layout(id_,attempt):
    r=Rng(id_*7919+attempt*104729+13)
    k=colors_of(id_); m=boxes_per_color(id_)
    nCols=3 if id_<4 else 4
    trays=[];boxes=[]
    for c in range(k):
        boxes+=[c]*m
        rem=m*BOX
        while rem>0:
            n=min(rem,3+r.nextInt(4))
            if 1<=rem-n<=2: n=rem
            trays.append(Tray(c,n)); rem-=n
    def shuffle(a):
        for i in range(len(a)-1,0,-1):
            j=r.nextInt(i+1); a[i],a[j]=a[j],a[i]
    shuffle(trays); shuffle(boxes)
    raw=[[] for _ in range(nCols)]
    for i,t in enumerate(trays): raw[i%nCols].append(t)
    p=arrow_chance(id_)
    cols=[]
    for ci,col in enumerate(raw):
        nc=[]
        for t in col:
            if r.next()<p:
                canL=ci>0; canR=ci<nCols-1
                if id_>=BOTH_FROM and canL and canR and r.next()<0.35: a=3
                elif canL and canR: a=1+r.nextInt(2)
                elif canL: a=1
                else: a=2
                nc.append(Tray(t.c,t.n,a))
            else: nc.append(t)
        cols.append(nc)
    every=2 if id_>250 else 3
    elevator=r.nextInt(SLOTS) if (id_>=20 and id_%every==0) else -1
    return cols,boxes,elevator

def floor_cap(i):
    """Минимум ленты, до которого стоит «поднимать» уровень: целевая кривая сложности."""
    return min(17, 4 + i//14)


def main():
    out = {}
    for i in range(1, 501):
        lim = 30 - slack(i)
        best = None; lo = floor_cap(i)
        for att in range(0, 40):
            cols, boxes, el = layout(i, att)
            mc = min_cap(cols, boxes, el, lim, 120000)
            if mc is None: continue
            if best is None or mc > best[1]: best = (att, mc)
            if mc >= lo: break
        out[i] = best
        if best is None: print("НЕТ РЕШЕНИЯ у уровня", i, file=sys.stderr)
    belts = [out[i][1] + slack(i) for i in range(1, 501)]
    print("private val ATTEMPT = mapOf(" + ", ".join(f"{i} to {a[0]}" for i, a in sorted(out.items()) if a[0] > 0) + ")")
    print("private val BELTS = intArrayOf(")
    for j in range(0, 500, 20): print("    " + ", ".join(map(str, belts[j:j + 20])) + ",")
    print(")")

if __name__ == "__main__":
    main()

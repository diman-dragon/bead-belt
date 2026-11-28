from PIL import Image, ImageDraw, ImageFilter, ImageChops
import colorsys

def hueshift(im, deg):
    im = im.convert('RGBA'); px = im.load(); w,h = im.size
    for y in range(h):
        for x in range(w):
            r,g,b,a = px[x,y]
            if a == 0: continue
            hh,s,v = colorsys.rgb_to_hsv(r/255,g/255,b/255)
            if s > 0.18:
                hh = (hh + deg/360) % 1
                r,g,b = colorsys.hsv_to_rgb(hh,s,v)
                px[x,y] = (int(r*255),int(g*255),int(b*255),a)
    return im

def tile(top, bottom, size=256, inset=16, radius=58):
    """глянцевая плитка в стиле Fluent 3D: градиент, блик, мягкая тень и ободок"""
    S = size*2; ins = inset*2; rad = radius*2
    base = Image.new('RGBA',(S,S),(0,0,0,0))
    grad = Image.new('RGBA',(S,S))
    gp = grad.load()
    for y in range(S):
        t = y/(S-1)
        c = tuple(int(top[i]*(1-t)+bottom[i]*t) for i in range(3))
        for x in range(S): gp[x,y] = c+(255,)
    mask = Image.new('L',(S,S),0)
    ImageDraw.Draw(mask).rounded_rectangle((ins,ins-6,S-ins,S-ins-6),rad,fill=255)
    shadow = Image.new('RGBA',(S,S),(0,0,0,0))
    sm = Image.new('L',(S,S),0)
    ImageDraw.Draw(sm).rounded_rectangle((ins,ins+10,S-ins,S-ins+10),rad,fill=110)
    sm = sm.filter(ImageFilter.GaussianBlur(12))
    shadow.paste((10,20,60,255),(0,0),sm)
    base.alpha_composite(shadow)
    base.paste(grad,(0,0),mask)
    gl = Image.new('L',(S,S),0)
    ImageDraw.Draw(gl).rounded_rectangle((ins+14,ins+4,S-ins-14,S//2-6),rad-10,fill=70)
    gl = ImageChops.multiply(gl,mask)
    base.paste((255,255,255,255),(0,0),gl)
    edge = Image.new('L',(S,S),0)
    ImageDraw.Draw(edge).rounded_rectangle((ins,ins-6,S-ins,S-ins-6),rad,outline=255,width=5)
    edge = ImageChops.multiply(edge.filter(ImageFilter.GaussianBlur(1)),mask)
    base.paste((255,255,255,255),(0,0),edge.point(lambda v:v//3))
    return base.resize((size,size),Image.LANCZOS)

def glyph_on(tile_im, glyph_path, scale=0.62, dy=-4):
    g = Image.open(glyph_path).convert('RGBA')
    bbox = g.getbbox(); g = g.crop(bbox)
    m = max(g.size); k = scale*256/m
    g = g.resize((max(1,int(g.width*k)),max(1,int(g.height*k))),Image.LANCZOS)
    sh = Image.new('RGBA',tile_im.size,(0,0,0,0))
    a = g.split()[3].point(lambda v:int(v*0.35))
    sh.paste((20,10,60,255),((256-g.width)//2,(256-g.height)//2+dy+6),a)
    sh = sh.filter(ImageFilter.GaussianBlur(4))
    out = tile_im.copy(); out.alpha_composite(sh)
    out.alpha_composite(g,((256-g.width)//2,(256-g.height)//2+dy))
    return out

hint  = glyph_on(tile((255,236,150),(255,170,40)), 'magic_wand.png', 0.78)             # золотая плитка, волшебная палочка (авторасклад)
undo  = hueshift(Image.open('right_arrow_curving_left.png'), 0)                         # синий → оранжево-красный
shuf  = hueshift(Image.open('shuffle_tracks_button.png'), 70)                                # синий → фиолетовый
clear = glyph_on(tile((140,240,170),(30,170,90)), 'broom.png', 0.74, dy=-2)                  # зелёная плитка, метла
for n,im in [('hint',hint),('undo',undo),('shuffle',shuf),('clear',clear)]:
    im.save(f'aid_{n}.png')
bg = Image.new('RGBA',(4*140+20,170),(30,70,170,255))
for i,n in enumerate(['hint','undo','shuffle','clear']):
    bg.alpha_composite(Image.open(f'aid_{n}.png').resize((128,128),Image.LANCZOS),(10+i*140,20))
bg.save('/home/claude/aids_preview.png')

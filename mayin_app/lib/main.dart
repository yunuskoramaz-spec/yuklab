import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';

void main() => runApp(const MayinApp());

const turq = Color(0xFF00B8BD);
const anth = Color(0xFF171B1E);
const surf = Color(0xFFF4F6F7);

class MayinApp extends StatelessWidget {
  const MayinApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'MAYIN',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: turq),
        scaffoldBackgroundColor: surf,
      ),
      home: const Home(),
    );
  }
}

enum Level { easy, medium, hard }

class Config {
  final int rows, cols, mines;
  final String name;
  const Config(this.rows, this.cols, this.mines, this.name);
}

Config cfg(Level l) {
  switch (l) {
    case Level.easy: return const Config(9, 9, 10, 'Kolay');
    case Level.medium: return const Config(16, 16, 40, 'Orta');
    case Level.hard: return const Config(16, 30, 99, 'Zor');
  }
}

class Home extends StatelessWidget {
  const Home({super.key});
  @override
  Widget build(BuildContext context) {
    Widget card(Level l, String size, String mines) {
      final c = cfg(l);
      return Expanded(
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => Game(level: l))),
          child: Ink(
            padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 8),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: const Color(0xFFDDE3E6)),
            ),
            child: Column(children: [
              Text(c.name, style: const TextStyle(fontWeight: FontWeight.w900)),
              const SizedBox(height: 6),
              Text(size, style: const TextStyle(fontSize: 12, color: Color(0xFF667177))),
              Text(mines, style: const TextStyle(fontSize: 11, color: Color(0xFF899196))),
            ]),
          ),
        ),
      );
    }

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: Padding(
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Spacer(),
                  Container(
                    width: 78, height: 78,
                    decoration: BoxDecoration(color: anth, borderRadius: BorderRadius.circular(24)),
                    child: const Icon(Icons.blur_on_rounded, size: 42, color: turq),
                  ),
                  const SizedBox(height: 18),
                  const Text('MAYIN',
                    style: TextStyle(fontSize: 42, fontWeight: FontWeight.w900, letterSpacing: 4, color: anth)),
                  const SizedBox(height: 4),
                  const Text('Klasik mayın tarlası. Reklamsız, hızlı ve temiz.',
                    style: TextStyle(color: Color(0xFF667177))),
                  const Spacer(),
                  const Text('Yeni oyun', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
                  const SizedBox(height: 10),
                  Row(children: [
                    card(Level.easy, '9×9', '10 mayın'),
                    const SizedBox(width: 9),
                    card(Level.medium, '16×16', '40 mayın'),
                    const SizedBox(width: 9),
                    card(Level.hard, '30×16', '99 mayın'),
                  ]),
                  const SizedBox(height: 16),
                  const Text('Dokun: aç  •  Uzun bas: bayrak  •  İki parmak: yakınlaştır',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 12, color: Color(0xFF7A858A))),
                  const Spacer(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class Cell {
  bool mine = false;
  bool open = false;
  bool flag = false;
  int around = 0;
}

class Game extends StatefulWidget {
  final Level level;
  const Game({super.key, required this.level});
  @override
  State<Game> createState() => _GameState();
}

class _GameState extends State<Game> {
  late Config c;
  late List<List<Cell>> board;
  bool placed = false;
  bool over = false;
  bool won = false;
  int elapsed = 0;
  Timer? timer;

  @override
  void initState() {
    super.initState();
    c = cfg(widget.level);
    reset();
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  void reset() {
    timer?.cancel();
    elapsed = 0; placed = false; over = false; won = false;
    board = List.generate(c.rows, (_) => List.generate(c.cols, (_) => Cell()));
    if (mounted) setState(() {});
  }

  void startTimer() {
    timer ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (!over && mounted) setState(() => elapsed++);
    });
  }

  void placeMines(int safeR, int safeC) {
    final cells = <Point<int>>[];
    for (int r=0; r<c.rows; r++) {
      for (int col=0; col<c.cols; col++) {
        if ((r-safeR).abs() <= 1 && (col-safeC).abs() <= 1) continue;
        cells.add(Point(r,col));
      }
    }
    cells.shuffle(Random());
    for (int i=0; i<c.mines; i++) board[cells[i].x][cells[i].y].mine = true;
    for (int r=0; r<c.rows; r++) {
      for (int col=0; col<c.cols; col++) {
        int n=0;
        for (int dr=-1; dr<=1; dr++) {
          for (int dc=-1; dc<=1; dc++) {
            final rr=r+dr, cc=col+dc;
            if (rr>=0 && rr<c.rows && cc>=0 && cc<c.cols && board[rr][cc].mine) n++;
          }
        }
        board[r][col].around=n;
      }
    }
    placed=true;
  }

  void tap(int r, int col) {
    if (over || board[r][col].flag || board[r][col].open) return;
    if (!placed) {
      placeMines(r,col);
      startTimer();
    }
    if (board[r][col].mine) {
      for (final row in board) for (final x in row) if (x.mine) x.open=true;
      over=true; won=false; timer?.cancel(); setState(() {}); result();
      return;
    }
    flood(r,col);
    checkWin();
    setState(() {});
  }

  void flood(int sr, int sc) {
    final q=<Point<int>>[Point(sr,sc)];
    final seen=<int>{};
    while(q.isNotEmpty) {
      final p=q.removeLast();
      final key=p.x*c.cols+p.y;
      if(!seen.add(key)) continue;
      final x=board[p.x][p.y];
      if(x.mine || x.flag) continue;
      x.open=true;
      if(x.around!=0) continue;
      for(int dr=-1;dr<=1;dr++) for(int dc=-1;dc<=1;dc++) {
        final rr=p.x+dr, cc=p.y+dc;
        if(rr>=0&&rr<c.rows&&cc>=0&&cc<c.cols&&!board[rr][cc].open) q.add(Point(rr,cc));
      }
    }
  }

  void flag(int r,int col) {
    if(over || board[r][col].open) return;
    setState(() => board[r][col].flag=!board[r][col].flag);
  }

  void checkWin() {
    final left=board.expand((e)=>e).any((x)=>!x.mine&&!x.open);
    if(!left) {
      over=true; won=true; timer?.cancel();
      for(final row in board) for(final x in row) if(x.mine) x.flag=true;
      result();
    }
  }

  void result() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if(!mounted) return;
      showModalBottomSheet(
        context: context,
        isDismissible: false,
        enableDrag: false,
        backgroundColor: Colors.transparent,
        builder: (_) => Container(
          padding: const EdgeInsets.fromLTRB(22,22,22,30),
          decoration: const BoxDecoration(color: Colors.white, borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
          child: SafeArea(
            top:false,
            child: Column(mainAxisSize: MainAxisSize.min, children:[
              Icon(won?Icons.emoji_events_rounded:Icons.warning_amber_rounded,
                size:50, color: won?turq:const Color(0xFFE1544B)),
              const SizedBox(height:10),
              Text(won?'Temizlendi':'Mayına bastın',
                style: const TextStyle(fontSize:24,fontWeight:FontWeight.w900)),
              const SizedBox(height:4),
              Text(time(elapsed), style: const TextStyle(color:Color(0xFF667177))),
              const SizedBox(height:18),
              FilledButton(
                onPressed:(){Navigator.pop(context);reset();},
                style:FilledButton.styleFrom(backgroundColor:anth,foregroundColor:Colors.white,minimumSize:const Size.fromHeight(52)),
                child:const Text('Tekrar Oyna')),
              TextButton(onPressed:(){Navigator.pop(context);Navigator.pop(context);},child:const Text('Ana menü')),
            ]),
          ),
        ),
      );
    });
  }

  String time(int s)=>'${(s~/60).toString().padLeft(2,'0')}:${(s%60).toString().padLeft(2,'0')}';
  int get flags => board.expand((e)=>e).where((x)=>x.flag).length;

  Color numColor(int n) {
    const m={1:Color(0xFF2474C6),2:Color(0xFF2E8B57),3:Color(0xFFD14D41),4:Color(0xFF5C4DB1),5:Color(0xFF9A4F2D),6:Color(0xFF168C96),7:Color(0xFF333333),8:Color(0xFF7A7A7A)};
    return m[n]??anth;
  }

  Widget stat(IconData icon,String value,String label)=>Container(
    padding:const EdgeInsets.symmetric(vertical:10,horizontal:14),
    decoration:BoxDecoration(color:Colors.white,borderRadius:BorderRadius.circular(16),border:Border.all(color:const Color(0xFFDDE3E6))),
    child:Row(mainAxisAlignment:MainAxisAlignment.center,children:[
      Icon(icon,size:20,color:turq),const SizedBox(width:8),
      Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text(value,style:const TextStyle(fontSize:16,fontWeight:FontWeight.w900)),
        Text(label,style:const TextStyle(fontSize:10,color:Color(0xFF7B858A))),
      ])
    ]));

  Widget cell(int r,int col,double size) {
    final x=board[r][col];
    Widget? child;
    if(x.flag&&!x.open) child=const Icon(Icons.flag_rounded,size:18,color:turq);
    else if(x.open&&x.mine) child=const Icon(Icons.brightness_1_rounded,size:15,color:Color(0xFFE1544B));
    else if(x.open&&x.around>0) child=Text('${x.around}',style:TextStyle(fontSize:max(12,size*.46),fontWeight:FontWeight.w900,color:numColor(x.around)));
    return GestureDetector(
      onTap:()=>tap(r,col), onLongPress:()=>flag(r,col),
      child:AnimatedContainer(
        duration:const Duration(milliseconds:80),
        width:size,height:size,alignment:Alignment.center,
        decoration:BoxDecoration(
          color:x.open?const Color(0xFFE9EEF0):Colors.white,
          border:Border.all(color:const Color(0xFFD4DCDF),width:.55)),
        child:child));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar:AppBar(
        backgroundColor:surf,
        title:Text(c.name,style:const TextStyle(fontWeight:FontWeight.w900)),
        actions:[IconButton(onPressed:reset,icon:const Icon(Icons.refresh_rounded)),const SizedBox(width:8)]),
      body:Column(children:[
        Padding(
          padding:const EdgeInsets.fromLTRB(16,4,16,12),
          child:Row(children:[
            Expanded(child:stat(Icons.flag_rounded,'${c.mines-flags}','Mayın')),
            const SizedBox(width:10),
            Expanded(child:stat(Icons.timer_outlined,time(elapsed),'Süre')),
          ])),
        Expanded(
          child:LayoutBuilder(builder:(context,cons){
            final base=c.cols<=9?40.0:31.0;
            final width=max(cons.maxWidth-24,c.cols*base);
            final s=width/c.cols;
            return InteractiveViewer(
              minScale:.75,maxScale:3.2,boundaryMargin:const EdgeInsets.all(80),constrained:false,
              child:Container(
                width:width,height:s*c.rows,margin:const EdgeInsets.all(12),
                clipBehavior:Clip.antiAlias,
                decoration:BoxDecoration(color:const Color(0xFFC7D0D3),borderRadius:BorderRadius.circular(16)),
                child:Column(children:List.generate(c.rows,(r)=>Row(children:List.generate(c.cols,(col)=>cell(r,col,s)))))));
          }))
      ]));
  }
}

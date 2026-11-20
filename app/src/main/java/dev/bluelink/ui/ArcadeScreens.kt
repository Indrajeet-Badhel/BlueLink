package dev.bluelink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bluelink.*
import dev.bluelink.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable private fun resumed():Boolean{
    val owner=LocalLifecycleOwner.current
    var active by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,_->active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    return active
}
@Composable fun ArcadeScreen(model:BlueLinkModel){
    var selected by rememberSaveable{mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick={if(selected.isEmpty())model.select(Destination.LINK)else selected=""}){Text(if(selected.isEmpty())"← Back to Link" else "← All games",color=Lime)}
        Text("MINI ARCADE",fontSize=29.sp,fontWeight=FontWeight.Black,color=Lime)
        Text("Offline play. No ads. Scores stay on this phone.",color=Muted,fontSize=12.sp)
        when(selected){
            "Snake"->SnakeScreen()
            "Sudoku"->SudokuScreen()
            "Goo Runner"->RunnerScreen()
            "Goo Blocks"->BlocksScreen()
            else->listOf("Snake" to "Turn, snack, survive the grid.","Sudoku" to "Unique-solution number puzzles.","Goo Runner" to "Jump over the incoming goo.","Goo Blocks" to "Rotate shapes and clear rows.").forEach{(name,description)->
                Surface(onClick={selected=name},color=Panel,shape=Chunky,border=BorderStroke(1.dp,Line),modifier=Modifier.fillMaxWidth().heightIn(min=96.dp)){
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(name,color=Yellow,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(description,color=Muted,fontSize=12.sp)}
                }
            }
        }
    }
}
@Composable private fun Score(name:String,score:Int){
    val context=LocalContext.current
    val preferences=remember{context.getSharedPreferences("arcade-scores",0)}
    var high by remember(name){mutableIntStateOf(preferences.getInt(name,0))}
    LaunchedEffect(score){if(score>high){high=score;preferences.edit().putInt(name,high).apply()}}
    Text("SCORE $score     BEST $high",color=Yellow,fontWeight=FontWeight.Bold)
}
@Composable private fun Controls(paused:Boolean,onPause:()->Unit,onRestart:()->Unit,over:Boolean){
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick=onPause,enabled=!over){Text(if(paused)"Resume" else "Pause")}
        Button(onClick=onRestart){Text(if(over)"Play again" else "Restart")}
    }
    if(over)Text("GAME OVER",color=Pink,fontWeight=FontWeight.Black,fontSize=20.sp)
}
@Composable private fun SnakeScreen(){
    var game by remember{mutableStateOf(SnakeGame())}
    var tick by remember{mutableIntStateOf(0)}
    var paused by rememberSaveable{mutableStateOf(false)}
    val active=resumed()
    LaunchedEffect(game,paused,active){while(active && !paused && !game.over){delay((240L-game.score*8).coerceAtLeast(70));game.tick();tick++}}
    val score=remember(tick,game){game.score}
    Score("snake",score)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Panel,Chunky).border(1.dp,Line,Chunky)){
        tick.hashCode();val cell=size.width/16
        game.body.forEachIndexed{i,c->drawRoundRect(if(i==0)Lime else Green,Offset(c.x*cell+1,c.y*cell+1),Size(cell-2,cell-2),androidx.compose.ui.geometry.CornerRadius(4f))}
        drawCircle(Pink,cell*.35f,Offset((game.food.x+.5f)*cell,(game.food.y+.5f)*cell))
    }
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
        Button(onClick={game.turn(0,-1)},modifier=Modifier.heightIn(min=48.dp)){Text("↑")}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick={game.turn(-1,0)},modifier=Modifier.heightIn(min=48.dp)){Text("←")}
            Button(onClick={game.turn(0,1)},modifier=Modifier.heightIn(min=48.dp)){Text("↓")}
            Button(onClick={game.turn(1,0)},modifier=Modifier.heightIn(min=48.dp)){Text("→")}
        }
    }
    Controls(paused,{paused=!paused},{game=SnakeGame();tick++;paused=false},game.over)
}
@Composable private fun RunnerScreen(){
    var game by remember{mutableStateOf(RunnerGame())}
    var tick by remember{mutableIntStateOf(0)}
    var paused by rememberSaveable{mutableStateOf(false)}
    val active=resumed()
    LaunchedEffect(game,paused,active){while(active && !paused && !game.over){delay(33);game.tick();tick++}}
    Score("runner",remember(tick,game){game.score})
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f).background(Panel,Chunky).clickable(enabled=!paused){game.jump()}){
        tick.hashCode();val ground=size.height*.82f
        drawLine(Line,Offset(0f,ground),Offset(size.width,ground),3f)
        drawRoundRect(Lime,Offset(size.width*.14f,ground-size.height*(.13f+game.height)),Size(size.width*.08f,size.height*.13f),androidx.compose.ui.geometry.CornerRadius(9f))
        drawCircle(Plum,size.width*.007f,Offset(size.width*.20f,ground-size.height*(.10f+game.height)))
        drawRect(Purple,Offset(game.obstacle*size.width,ground-size.height*.16f),Size(size.width*.065f,size.height*.16f))
    }
    Button(onClick={game.jump()},enabled=!paused && !game.over,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("JUMP")}
    Controls(paused,{paused=!paused},{game=RunnerGame();tick++;paused=false},game.over)
}
@Composable private fun BlocksScreen(){
    var game by remember{mutableStateOf(BlocksGame())}
    var tick by remember{mutableIntStateOf(0)}
    var paused by rememberSaveable{mutableStateOf(false)}
    val active=resumed()
    LaunchedEffect(game,paused,active){while(active && !paused && !game.over){delay((650L-game.lines*15).coerceAtLeast(90));game.tick();tick++}}
    Score("blocks",remember(tick,game){game.score})
    Canvas(Modifier.fillMaxWidth().height(360.dp).background(Panel,Chunky)){
        tick.hashCode();val cell=minOf(size.width/10,size.height/20);val left=(size.width-cell*10)/2
        game.board.forEachIndexed{i,n->if(n!=0)drawRect(Purple,Offset(left+i%10*cell,i/10*cell),Size(cell-1,cell-1))}
        if(!game.over)game.shape.forEach{c->drawRect(Lime,Offset(left+(game.x+c.x)*cell,(game.y+c.y)*cell),Size(cell-1,cell-1))}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf("←" to {game.move(-1)},"Rotate" to {game.rotate()},"→" to {game.move(1)},"Drop" to {game.drop()}).forEach{(name,click)->
            Button(onClick={click();tick++},enabled=!paused && !game.over,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(3.dp)){Text(name,fontSize=12.sp)}
        }
    }
    Controls(paused,{paused=!paused},{game=BlocksGame();tick++;paused=false},game.over)
}
@Composable private fun SudokuScreen(){
    var difficulty by rememberSaveable{mutableIntStateOf(0)}
    var seed by rememberSaveable{mutableIntStateOf(1)}
    var selected by rememberSaveable{mutableIntStateOf(-1)}
    var revision by remember{mutableIntStateOf(0)}
    var seconds by remember(seed,difficulty){mutableIntStateOf(0)}
    var paused by rememberSaveable{mutableStateOf(false)}
    val active=resumed()
    val game by produceState<SudokuGame?>(null,difficulty,seed){value=null;value=withContext(Dispatchers.Default){SudokuGame(difficulty)}}
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Easy","Medium","Hard").forEachIndexed{i,s->FilterChip(selected=i==difficulty,onClick={difficulty=i;selected=-1},label={Text(s)})}}
    val current=game
    if(current==null){CircularProgressIndicator(color=Lime);Text("Generating a unique puzzle…",color=Muted);return}
    val complete=remember(revision,current){current.complete()}
    LaunchedEffect(current,active,paused,complete){while(active && !paused && !complete){delay(1000);seconds++}}
    Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')} • ${if(complete)"Solved!" else if(paused)"Paused" else "Find the missing numbers"}",color=Yellow)
    Column(Modifier.fillMaxWidth().background(Panel).border(2.dp,Lime)){
        repeat(9){row->Row(Modifier.fillMaxWidth()){
            repeat(9){col->val index=row*9+col;val number=remember(revision,current){current.cells[index]}
                val conflict=remember(revision,current){current.conflict(index)}
                Box(Modifier.weight(1f).aspectRatio(1f).background(if(selected==index)Purple else if(conflict)Color(0xFF6B173E) else Panel)
                    .border(if(row%3==0 || col%3==0)1.dp else .4.dp,Line).clickable(enabled=!paused && !complete){selected=index},contentAlignment=Alignment.Center){
                    Text(if(number==0 || paused)"" else number.toString(),color=if(conflict)Pink else if(current.clues[index])Paper else Lime,fontWeight=if(current.clues[index])FontWeight.Bold else FontWeight.Normal,fontSize=18.sp)
                }
            }
        }}
    }
    listOf((1..5).toList(),(6..9).toList()+0).forEach{numbers->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
        numbers.forEach{n->Button(onClick={current.set(selected,n);revision++},enabled=!paused && !complete && selected>=0 && !current.clues[selected],modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)){Text(if(n==0)"Erase" else "$n",fontSize=12.sp)}}
    }}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={paused=!paused}){Text(if(paused)"Resume" else "Pause")}
        Button(onClick={seed++;selected=-1;paused=false}){Text("New puzzle")}
    }
}

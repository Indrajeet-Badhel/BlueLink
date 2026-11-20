package dev.bluelink.protocol

import kotlin.random.Random

data class Cell(val x:Int,val y:Int)
class SnakeGame(private val random:Random=Random.Default) {
    var body=listOf(Cell(8,8),Cell(7,8),Cell(6,8));private set
    var direction=Cell(1,0);private set
    private var next=direction
    var food=Cell(12,8);private set
    var score=0;private set
    var over=false;private set
    fun turn(x:Int,y:Int) { if(kotlin.math.abs(x)+kotlin.math.abs(y)==1 && Cell(x,y)!=Cell(-direction.x,-direction.y))next=Cell(x,y) }
    fun tick() {
        if(over)return
        direction=next
        val head=Cell(body[0].x+direction.x,body[0].y+direction.y)
        val eating=head==food
        if(head.x !in 0..15 || head.y !in 0..15 || head in (if(eating)body else body.dropLast(1))){over=true;return}
        body=listOf(head)+(if(eating)body else body.dropLast(1))
        if(eating){score++;val free=(0..15).flatMap{y->(0..15).map{x->Cell(x,y)}}.filter{it !in body};if(free.isEmpty())over=true else food=free[random.nextInt(free.size)]}
    }
}

class SudokuGame(difficulty:Int,random:Random=Random.Default) {
    val solution:IntArray
    val clues:BooleanArray
    val cells:IntArray
    init {
        val nums=(1..9).shuffled(random)
        val rows=(0..2).shuffled(random).flatMap{band->(0..2).shuffled(random).map{band*3+it}}
        val cols=(0..2).shuffled(random).flatMap{band->(0..2).shuffled(random).map{band*3+it}}
        solution=IntArray(81){i->val r=rows[i/9];val c=cols[i%9];nums[(r*3+r/3+c)%9]}
        cells=solution.copyOf()
        val target=listOf(40,33,28)[difficulty.coerceIn(0,2)]
        for(i in (0..80).shuffled(random)) {
            if(cells.count{it!=0}<=target)break
            val n=cells[i];cells[i]=0
            if(solutions(cells.copyOf())!=1)cells[i]=n
        }
        clues=BooleanArray(81){cells[it]!=0}
    }
    fun set(index:Int,value:Int){if(index in 0..80 && !clues[index] && value in 0..9)cells[index]=value}
    fun conflict(i:Int):Boolean {
        val n=cells[i];if(n==0)return false
        return (0..80).any{j->j!=i && cells[j]==n && (j/9==i/9 || j%9==i%9 || (j/27==i/27 && j%9/3==i%9/3))}
    }
    fun complete()=cells.none{it==0} && (0..80).none(::conflict)
    companion object {
        fun solutions(grid:IntArray,limit:Int=2):Int {
            var best=-1;var candidates=emptyList<Int>()
            for(i in 0..80)if(grid[i]==0){
                val possible=(1..9).filter{n->(0..80).none{j->grid[j]==n && (j/9==i/9 || j%9==i%9 || (j/27==i/27 && j%9/3==i%9/3))}}
                if(possible.isEmpty())return 0
                if(best<0 || possible.size<candidates.size){best=i;candidates=possible;if(possible.size==1)break}
            }
            if(best<0)return if((0..80).all{i->(0..80).none{j->j!=i && grid[j]==grid[i] && (j/9==i/9 || j%9==i%9 || (j/27==i/27 && j%9/3==i%9/3))}})1 else 0
            var count=0
            for(n in candidates){grid[best]=n;count+=solutions(grid,limit-count);if(count>=limit)break}
            grid[best]=0;return count
        }
    }
}

class RunnerGame(private val random:Random=Random.Default) {
    var height=0f;private set
    private var velocity=0f
    var obstacle=1.2f;private set
    var score=0;private set
    var over=false;private set
    fun jump(){if(height==0f && !over)velocity=1.9f}
    fun tick(dt:Float=.033f){
        if(over)return
        height=(height+velocity*dt).coerceAtLeast(0f);velocity=if(height==0f)0f else velocity-4f*dt
        obstacle-=(.45f+score*.018f)*dt
        if(obstacle in .10f.. .24f && height<.15f){over=true;return}
        if(obstacle<-.1f){score++;obstacle=1.1f+random.nextFloat()*.6f}
    }
}

class BlocksGame(private val random:Random=Random.Default) {
    val board=IntArray(200)
    private val shapes=listOf(listOf(Cell(0,1),Cell(1,1),Cell(2,1),Cell(3,1)),listOf(Cell(1,0),Cell(2,0),Cell(1,1),Cell(2,1)),
        listOf(Cell(1,0),Cell(0,1),Cell(1,1),Cell(2,1)),listOf(Cell(0,0),Cell(0,1),Cell(1,1),Cell(2,1)),
        listOf(Cell(2,0),Cell(0,1),Cell(1,1),Cell(2,1)),listOf(Cell(1,0),Cell(2,0),Cell(0,1),Cell(1,1)),listOf(Cell(0,0),Cell(1,0),Cell(1,1),Cell(2,1)))
    var shape=shapes[random.nextInt(shapes.size)];private set
    var x=3;private set
    var y=0;private set
    var score=0;private set
    var lines=0;private set
    var over=false;private set
    fun valid(s:List<Cell>,px:Int,py:Int)=s.all{val xx=it.x+px;val yy=it.y+py;xx in 0..9 && yy in 0..19 && board[yy*10+xx]==0}
    fun move(dx:Int){if(!over && valid(shape,x+dx,y))x+=dx}
    fun rotate(){if(over)return;val rotated=shape.map{Cell(2-it.y,it.x)};for(kick in listOf(0,-1,1,-2,2))if(valid(rotated,x+kick,y)){shape=rotated;x+=kick;return}}
    fun tick(){
        if(over)return
        if(valid(shape,x,y+1)){y++;return}
        shape.forEach{board[(it.y+y)*10+it.x+x]=1}
        var removed=0;var row=19
        while(row>=0){
            if((0..9).all{board[row*10+it]!=0}){for(r in row downTo 1)for(c in 0..9)board[r*10+c]=board[(r-1)*10+c];for(c in 0..9)board[c]=0;removed++}
            else row--
        }
        lines+=removed;score+=listOf(0,100,300,500,800)[removed.coerceAtMost(4)]
        shape=shapes[random.nextInt(shapes.size)];x=3;y=0;if(!valid(shape,x,y))over=true
    }
    fun drop(){if(over)return;while(valid(shape,x,y+1))y++;tick()}
}

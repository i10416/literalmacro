
import scala.quoted.*
import scala.compiletime.constValue
import io.circe.Encoder
import io.circe.Json
import org.typelevel.jawn.*
import org.typelevel.jawn.FContext
import scala.util.Success
import org.typelevel.jawn.Parser
import scala.util.Failure
import io.circe.JsonNumber
import io.circe.Decoder
import io.circe.KeyEncoder

object JsonLiteralOps {

  implicit inline def instance[T,S <: T & Singleton]:KeyEncoder[S] = new KeyEncoder[S] {
    def apply(a: S): String = constValue[S].toString
  }
  
  extension (inline sc:StringContext) {
    inline final def json(inline args:Any*):Json =  ${ JsonLiteralMacros.jsonImpl('sc,'args) }
  }
}


private object JsonLiteralMacros {


  def jsonImpl(sc:Expr[StringContext],args:Expr[Seq[Any]])(using q:Quotes) :Expr[Json]= {
    import q.reflect.*
    val stringParts = sc match {
      case '{StringContext($parts:_*)} => parts.valueOrAbort
    }
    

    val replacements = args match {
      case Varargs(argExprs) =>
        argExprs.map{
          case '{$arg: tp} =>
            Replacement(stringParts,arg)
        }
      case other => report.error("Invalid arguments for json literal.");Nil
    }

    val jsonString = stringParts.zip(replacements.map(_.placeholder)).foldLeft("") {
      case (acc,(part,placeholder)) =>
        val qm = "\""
        s"$acc$part$qm$placeholder$qm"   
    } + stringParts.last

    inline given Facade[Expr[Json]] with {

      private def toJsonKey(s:String):Expr[String] =  replacements.find(_.placeholder == s )
      .fold(Expr(s.toString))(_.asKey)
      private def toJsonString(s:String):Expr[Json] = replacements.find(_.placeholder == s )
      .fold{ val strExpr = Expr(s.toString);'{ Json.fromString($strExpr)  }   }(_.asJson)
      def arrayContext(index: Int): FContext[Expr[Json]] = new FContext.NoIndexFContext[Expr[Json]] {
        private var values:Expr[List[Json]] = Expr(Nil)

        def isObj: Boolean = false
  
        def add(s: CharSequence): Unit = {
          val strExpr = toJsonString(s.toString)
          values = '{ $strExpr :: $values }
        }
        def add(v: Expr[Json]): Unit =  values = '{  $v :: ${values} }
        def finish(): Expr[Json] = '{Json.arr($values.reverse:_*)}
      }
      def jfalse(index: Int): Expr[Json] = '{Json.False}
      def jnull(index: Int): Expr[Json] = '{Json.Null}
      inline def jnum(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Expr[Json] =
        val str = Expr(s.toString)
        '{JsonNumber.fromString($str).map(Json.fromJsonNumber(_)).getOrElse(throw Exception("Invalid json number."))}      
      def jstring(s: CharSequence, index: Int): Expr[Json] = toJsonString(s.toString)
      def jtrue(index: Int): Expr[Json] = '{Json.True}
      def objectContext(index: Int): FContext[Expr[Json]] = new FContext.NoIndexFContext[Expr[Json]] {
          private[this] var fields: Expr[List[(String,Json)]] = Expr(Nil)
          private[this] var key:String = null
          
          def isObj: Boolean = true
          def add(s: CharSequence): Unit = {
            if(key.eq(null)){
              key = s.toString
            }else {
              val keyExpr = toJsonKey(key)
              val value = toJsonString(s.toString)
               fields = '{  ($keyExpr,$value) :: $fields }
            }
          }
          def add(v: Expr[Json]): Unit = {
              val keyExpr = toJsonKey(key)
              fields = '{   ($keyExpr,$v) :: $fields}
              key = null
          }
          def finish(): Expr[Json] = '{ Json.obj($fields.reverse:_*) }
      }
      def singleContext(index: Int): FContext[Expr[Json]] = new FContext.NoIndexFContext[Expr[Json]] {
          private[this] var value: Expr[Json] = null

          def isObj: Boolean = false  
          def add(s: CharSequence): Unit = value =  toJsonString(s.toString)
          def add(v: Expr[Json]): Unit = value = v
          def finish(): Expr[Json] = value
      }
    }

    Parser.parseFromString[Expr[Json]](jsonString) match {
      case Success(jsonExpr)=> jsonExpr
      case Failure(e) => report.error(e.toString);'{???}
    }
  }
}


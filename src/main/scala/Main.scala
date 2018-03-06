import it.reply.data.pasquali.Storage

object Main {

  def main(args: Array[String]): Unit = {

    println (Storage().init("local[*]", "ciaooo ne", true).spark)

  }

}

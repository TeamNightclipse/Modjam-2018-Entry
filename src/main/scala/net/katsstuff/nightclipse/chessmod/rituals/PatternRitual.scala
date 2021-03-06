package net.katsstuff.nightclipse.chessmod.rituals

import scala.collection.JavaConverters._

import net.katsstuff.nightclipse.chessmod.{ChessBlocks, ChessMonsterSpawner, MonsterSpawnerSettings}
import net.katsstuff.nightclipse.chessmod.entity.EntityOngoingRitual
import net.minecraft.block.state.pattern.BlockPattern
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.{AxisAlignedBB, BlockPos}
import net.minecraft.util.text.{ITextComponent, TextComponentString, TextComponentTranslation}
import net.minecraft.world.World

case class PatternRitual(
    pattern: BlockPattern,
    donePattern: BlockPattern,
    removeBlocks: (EntityOngoingRitual, BlockPattern.PatternHelper) => Unit,
    duration: Int,
    reward: EntityOngoingRitual => ItemStack,
    spawnerSettings: MonsterSpawnerSettings
) extends Ritual {

  override def beginActivation(
      world: World,
      pos: BlockPos,
      player: EntityPlayer
  ): Either[ITextComponent, EntityOngoingRitual] = {
    val helper = pattern.`match`(world, pos)
    if (helper != null) {
      val isAllowed = if (allowsOtherUp) true else helper.getUp.getAxis.isHorizontal
      if (isAllowed) {
        val king = for {
          palm   <- 0 until pattern.getPalmLength
          thumb  <- 0 until pattern.getThumbLength
          finger <- 0 until pattern.getFingerLength
          state = helper.translateOffset(palm, thumb, finger)
          if state.getBlockState.getBlock == ChessBlocks.PieceKing
        } yield state

        king match {
          case Seq(onlyKing) => Right(new EntityOngoingRitual(player, this, onlyKing.getPos, player.world))
          case Seq()         => Left(new TextComponentTranslation("ritual.error.kingnotfound"))
          case _             => Left(new TextComponentTranslation("ritual.error.multiplekings"))
        }
      } else Left(new TextComponentTranslation("ritual.error.wrongdirection"))
    } else Left(new TextComponentTranslation("ritual.error.wrongblocks.block"))
  }

  override def hasCorrectPlacement(world: World, pos: BlockPos): Boolean = {
    val helper = pattern.`match`(world, pos.add(0, 0, 0))
    if (helper != null) {
      if (allowsOtherUp) true else helper.getUp.getAxis.isHorizontal
    } else false
  }

  override def tickServer(entity: EntityOngoingRitual): Option[Either[ITextComponent, ItemStack]] = {
    val helper = donePattern.`match`(entity.world, entity.kingPos)
    if(helper == null) {
      Some(Left(new TextComponentTranslation("ritual.error.interrupted.specialblock")))
    }
    else if (entity.ticksExisted > duration) {
      removeBlocks(entity, helper)
      Some(Right(reward(entity)))
    }
    else {
      ChessMonsterSpawner.spawnAround(entity, Some(entity.kingPos), intensity(entity))(spawnerSettings)
      None
    }
  }

  override def intensity(entity: EntityOngoingRitual): Float = entity.ticksExisted / duration.toFloat

  override def size: Int = pattern.getPalmLength * pattern.getThumbLength * pattern.getFingerLength

  override def doPlayerInfo(entity: EntityOngoingRitual): Unit = {
    val remainingTicks      = duration - entity.ticksExisted
    val remainingSecondsAll = remainingTicks / 20
    val remainingMinutes    = remainingSecondsAll / 60
    val remainingSeconds    = remainingSecondsAll % 60

    val message = new TextComponentString(s"$remainingMinutes:$remainingSeconds")
    entity.world
      .getEntitiesWithinAABB(classOf[EntityPlayer], new AxisAlignedBB(entity.kingPos).grow(32D))
      .asScala
      .foreach(_.sendMessage(message))
  }

  def allowsOtherUp: Boolean = false
}

import util.Util.{getNeighbors, toGrayScale}
import util.{Pixel, Util}

import scala.annotation.tailrec

// Online viewer: https://0xc0de.fr/webppm/
object Solution {
  type Image = List[List[Pixel]]
  type GrayscaleImage = List[List[Double]]

  // Prerequisites: Convert input PPM format (List[Char]) to Image
  def fromStringPPM(image: List[Char]): Image = {
    val lines = image.mkString.split("\n")

    // Extract header information (width and height)
    val header = lines.take(3)

    val widthHeight = header(1).split(" ")
    val width = widthHeight(0).toInt
    val height = widthHeight(1).toInt

    // Parse data and create Pixels from triplet RGB values
    val data = lines.drop(3).flatMap(_.split(" ")).map(_.toInt)

    // Arrange Pixels into an Image format (List of List of Pixels)
    val pixels = data.grouped(3).map {
      triplet => Pixel(triplet(0), triplet(1), triplet(2))
    }.toList.grouped(width).toList.map(_.toList)

    // Return the Image
    pixels.take(height)
  }

  // Prerequisites: Convert Image to output PPM format (List[Char])
  def toStringPPM(image: Image): List[Char] = {
    // Extract width and height from the Image
    val width = image match {
      case Nil => 0
      case head :: _ => head.size
    }
    val height = image.size

    // Construct PPM header
    val header = s"P3\n$width $height\n255\n"

    // Flatten the Image and convert Pixels to string representation
    val pixelData = image.flatten.map(p => s"${p.red} ${p.green} ${p.blue}\n").mkString

    // Concatenate the header and pixel data and return as List[Char]
    header.toList ++ pixelData.toList
  }

  // Concatenate two images vertically
  def verticalConcat(image1: Image, image2: Image): Image = {
    // Concatenate two images vertically and return the resulting Image
    val height1 = image1.size
    val height2 = image2.size

    val width1 = if (height1 > 0) image1.head.size else 0
    val width2 = if (height2 > 0) image2.head.size else 0

    // The images must have the same number of columns for vertical concatenation
    if (width1 != width2) {
      throw new IllegalArgumentException("Different no of columns")
    }

    // Concatenate the images
    image1 ++ image2
  }

  // Concatenate two images horizontally
  def horizontalConcat(image1: Image, image2: Image): Image = {
    // Concatenate two images horizontally and return the resulting Image
    val width1 = image1.head.size
    val width2 = image2.head.size

    val height1 = if (width1 > 0) image1.size else 0
    val height2 = if (width2 > 0) image2.size else 0

    // The images must have the same number of rows for horizontal concatenation
    if (height1 != height2) {
      throw new IllegalArgumentException("Different no of rows")
    }

    // Concatenate the images
    image1.zip(image2).map ((row1, row2) => row1 ++ row2)
  }

  // Rotate the image by a given number of degrees
  def rotate(image: Image, degrees: Integer): Image = {
    // Rotate the image by the specified number of degrees (multiple of 90 degrees)
    // Perform necessary transformations based on the rotation angle
    val noRotations = (degrees % 360) / 90
    val rotatedImage =
      noRotations match {
        case 1 => image.map(_.reverse).transpose
        case 2 => image.reverse.map(_.reverse)
        case 3 => image.reverse.transpose
        case _ => image // 360 - no need for rotation
      }
    // Return the rotated Image
    rotatedImage
  }

  // Apply convolution using a given kernel to the grayscale image
  // Define the Gaussian blur kernel
  val gaussianBlurKernel: GrayscaleImage = List[List[Double]](
    List( 1, 4, 7, 4, 1),
    List( 4,16,26,16, 4),
    List( 7,26,41,26, 7),
    List( 4,16,26,16, 4),
    List( 1, 4, 7, 4, 1)
  ).map(_.map(_ / 273))
  // Define the Gx kernel for edge detection
  val Gx : GrayscaleImage = List(
    List(-1, 0, 1),
    List(-2, 0, 2),
    List(-1, 0, 1)
  )
  // Define the Gy kernel for edge detection
  val Gy : GrayscaleImage = List(
    List( 1, 2, 1),
    List( 0, 0, 0),
    List(-1,-2,-1)
  )

  // Perform convolution operation on the grayscale image using the given kernel
  def applyConvolution(image: GrayscaleImage, kernel: GrayscaleImage) : GrayscaleImage = {
    // Kernel is ALWAYS a squared matrix: (2 * kernel_center + 1) * (2 * kernel_center + 1)
    val kernel_center = (kernel.size / 2.0).toInt

    // Centered on each pixel from image, each element in the kernel is multiplied with
    // Corresponding grayscale pixel value and then summed => output pixel
    val neighbors = getNeighbors(image, kernel_center)
    val kernel_array = kernel.flatten

    // Perform element-wise multiplication between the 2 arrays.
    // The result is a 1D array, which are summed up to a single value.
    neighbors.map(_.map(neighbor => {
      val neighbors_array = neighbor.flatten
      val res_convolution = (kernel_array zip neighbors_array).map(_ * _)
      res_convolution.sum.toDouble
    }))
  }

  // Detect edges in the given image using convolution and thresholding
  def edgeDetection(image: Image, threshold : Double): Image = {
    // STEP I: Convert the input image to grayscale
    val grayScaleImage = image.map(_.map(toGrayScale))

    // STEP II: Apply Gaussian blur convolution to the grayscale image
    val blurGaussianImage = applyConvolution(grayScaleImage, gaussianBlurKernel)
    // STEP III: Apply Gx and Gy kernels using convolution on the blurred image
    val Mx = applyConvolution(blurGaussianImage, Gx)
    val My = applyConvolution(blurGaussianImage, Gy)

    // STEP IV: Calculate the magnitude gradient from the convolutions
    val edges = Mx.zip(My).map((lineX, lineY) => lineX.zip(lineY).map((mx, my) => mx.abs + my.abs))

    // STEP V: Threshold the magnitude values to produce a binary image
    val edgesImage = edges.map(_.map(_.toDouble))
    // STEP VI: Based on value compared to threshold put black / white
    val tresholded = edgesImage.map(_.map(value =>
      if (value >= threshold)
        Pixel(255, 255, 255)
      else
        Pixel(0, 0, 0)
    )
    )

    // Return the resulting edge-detected Image
    tresholded
  }

  // Helper function: Generate a row of Pascal's triangle with a given size and modulus
  def generateTriangle(size: Int, mod: Int): List[List[Int]] = {
    /* Explanation: calculateNextLine with a little example
                    prevLine     _start         _end         mod per element from list
          1      -> (1) =>       (0,1)       + (1,0) =       (1,1) % mod
         1 1     -> (1,1) =>     (0,1,1)     + (1,1,0) =     (1,2,1) % mod
        1 2 1    -> (1,2,1) =>   (0,1,2,1)   + (1,2,1,0) =   (1,3,3,1) % mod
       1 3 3 1   -> (1,3,3,1) => (0,1,3,3,1) + (1,3,3,1,0) = (1,4,6,4,1) % mod
      . . . . .      . . . . .      . . . . .      . . . . .      . . . . .
    */
    def calculateNextLine(prevLine: List[Int]): List[Int] = {
      // Generate a row of Pascal's triangle based on the specified size and modulus
      val paddedPrevLine_start = List(0) ++ prevLine
      val paddedPrevLine_end = prevLine ++ List(0)
      // Return the resulting triangle as a List of List of Integers
      paddedPrevLine_end.zip(paddedPrevLine_start).map((a,b) => (a + b) % mod)
    }

    @tailrec
    /* Build Triangle Row by Row */
    def buildTriangle(n: Int, triangle: List[List[Int]]): List[List[Int]] = {
      if (n >= size) triangle // Base Case: last row was added in the triangle
      else {
        // Inductive Case: concatenate next row and pre-append it in the triangle list
        val nextLine = calculateNextLine(triangle.head)
        buildTriangle(n + 1, nextLine :: triangle)
      }
    }
    // After the call buildTriangle with an initial row 1
    // The result is list reversed, because of tailrec implementation
    buildTriangle(1, List(List(1))).reverse
  }

  // Create an image with colors determined by a function and a modular Pascal triangle
  def moduloPascal(m: Int, funct: Int => Pixel, size: Int): Image = {
    // STEP I. Compute Pascal Triangle - specified size + no of colors m
    val triangle = generateTriangle(size, m)

    // Last line is the biggest one from the pascal triangle, matrix of pixels
    val max_width = triangle.last.size

    // STEP II. Size of the image is determined by the maximum width of the lines
    val max_width_image = triangle.map(
      line => {
        val pixels = line.map(funct)
        val padding = List.fill(max_width - line.size)(Pixel(0, 0, 0))
        padding ++ pixels
      }
    )

    // STEP III. BuildImage helps to build the image from the list of pixels
    @tailrec
    // acc - accumulates the lines in reverse order
    def buildImage(max_width_image: Image, acc: Image): Image =
      max_width_image match {
        case Nil => acc.reverse
        // Take pixels from each line and append to a row accumulator
        case line :: rest => buildImage(rest, line.reverse :: acc)
      }
    // Returns the image constructed
    buildImage(max_width_image, Nil).take(size)
  }
}

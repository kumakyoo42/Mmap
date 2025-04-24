# MMap - a map generator for micromapped areas

***Note: This software is not maintained.***

## Usage

    java MMap <oma file> <exterior> [x]

Make sure to include the [OMA library](https://github.com/kumakyoo42/OmaLibJava) when compiling and
running this program.

The `<exterior>` is a list of comma separated coordinates of the
outline of the area you want to map, all coordinates are in WGS84,
longitute before latitude.

Add an `x` at the end, if you want the output to be tiles useable for a
slippy map. Without the x you'll get a large image file called
`image.png`.

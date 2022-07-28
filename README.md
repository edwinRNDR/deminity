# DEMINITY 

_Half a unity?_

This is the "demo engine" that is used for Limp Ninja's _20/20_ demo. In this repository you will find both the engine sources
and the assets used for the _20/20_ demo.

[![Limp Ninja - 20/20](https://img.youtube.com/vi/TrGJt-FxRKw/0.jpg)](https://www.youtube.com/watch?v=TrGJt-FxRKw)


## Building and running

Import the gradle project into Intellij (2020.2.3 or newer), select the `Deminity.kt` file and click on the 
green play wedge next to `fun main`.

## Using the Deminity program

When the program runs you press the `tab` key to reveal a timeline.

Some other keys to press:

 * `arrow down` set cue point
 * `arrow up` jump to cue point
 * `arrow left` jump back in time
 * `arrow right` jump forward in time
 * `space` toggle pause
 * `q` halve the playback pitch
 * `w` double the playback pitch
 * `m` toggle sound mute
 * `o` toggle post-processing
 
 ## Further reading
 
 The code for Deminity makes use of 
 
 * [OPENRNDR](https://github.com/openrndr/openrndr)
 * [ORX](https://github.com/openrndr/openrndr)
   * [orx-keyframer](https://github.com/openrndr/orx/tree/master/orx-jvm/orx-keyframer) for practically all animation
   * [orx-file-watcher](https://github.com/openrndr/orx/tree/master/orx-jvm/orx-file-watcher) for hot-reloading support
   * [orx-shapes](https://github.com/openrndr/orx/tree/master/orx-shapes) notably the bezier patches in some of the tools.
   * [orx-fx](https://github.com/openrndr/orx/tree/master/orx-fx) for all post-processing

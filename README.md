# play-chunked-file-uploader
[![Build Status](https://travis-ci.org/AkihiroTAKASE/play-chunked-file-uploader.svg?branch=master)](https://travis-ci.org/AkihiroTAKASE/play-chunked-file-uploader)

# Overview
This application demonstrates an asynchronous file uploading by using Akka as its core feature.
In order to perform the asynchronous uploade, [ResumableJS][3] is also used as a part of the upload client.
[1]:http://akka.io/
[2]:https://www.playframework.com/
[3]:http://www.resumablejs.com/

# What is the purpose?

This application has three objectives below:

 1. Avoid blocking I/O in the default thread pool of Play Framework.
 2. Shorten the time when a upload operation uses a blocking I/O in a thread.
 3. Reduce the memory usage when the application receives a data.

For this reasons, chunks are uploaded instead of a file itself. And uploaded chunks are concatenated in a thread which Play Akka plugin has instead of Play Framework default thread pool.

# Copyright and license
The MIT License

Copyright (c) 2015 Akihiro TAKASE http://akihiro-takase.tumblr.com/

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

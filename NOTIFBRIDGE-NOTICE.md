# NotifBridge adaptation notice

The notification provider and watch receiver methods in this candidate are adapted
from shinvou/NotifBridge, revision 0753ba7168d03c6f4363b6a369422fec25be7538:
https://github.com/shinvou/NotifBridge/tree/0753ba7168d03c6f4363b6a369422fec25be7538

Adapted behaviors: complete SDK identities in reverse respond/clear commands;
receiver-correlated alert receipts; preserving
original notification dates and keeping updates quiet. The Pebble binary transport,
authentication, sequencing and command digest deduplication remain project code.

This implementation removes the quiet-notification preference. It requests an alert only
when iOS reports notificationCanAlert and no Focus suppression. Eligible quiet
items can still alert on the watch; previously alerted or ineligible items are
forwarded quietly with their original dates. This uses Apple's explicit alert
eligibility and Focus flags rather than overriding every SDK add.

The upstream MIT license follows.

MIT License

Copyright (c) 2026 Timm Kandziora

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

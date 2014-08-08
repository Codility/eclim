" Author:  Kannan Rajah
"
" Description: {{{
"   see http://eclim.org/vim/java/debug.html
"
" License:
"
" Copyright (C) 2005 - 2014  Eric Van Dewoestine
"
" This program is free software: you can redistribute it and/or modify
" it under the terms of the GNU General Public License as published by
" the Free Software Foundation, either version 3 of the License, or
" (at your option) any later version.
"
" This program is distributed in the hope that it will be useful,
" but WITHOUT ANY WARRANTY; without even the implied warranty of
" MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
" GNU General Public License for more details.
"
" You should have received a copy of the GNU General Public License
" along with this program.  If not, see <http://www.gnu.org/licenses/>.
"
" }}}

" Script Variables {{{
let s:breakpoint_sign_name = 'breakpoint_mark'

let s:command_start =
  \ '-command java_debug_start -n "<target_name>" -h "<host>" -p "<port>" -v "<vim_servername>"'

let s:command_control =
  \ '-command java_debug_control -a "<action>"'

let s:command_toggle_breakpoint =
  \ '-command java_debug_toggle_breakpoint -p "<project>" -f "<file>" -l "<line_num>"'

let s:command_breakpoint =
  \ '-command java_debug_breakpoint -a "<action>"'

let s:command_step =
  \ '-command java_debug_step -a "<action>"'

let s:command_vars =
  \ '-command java_debug_vars'

let s:command_stackframe =
  \ '-command java_debug_stackframe'
" }}}

function! eclim#java#debug#DebugStart(host, port) " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  if (v:servername == '')
    call eclim#util#EchoError("Error: To debug, start VIM in server mode.
          \ Usage: vim --servername <name>")
    return
  endif

  if (a:host == '')
    call eclim#util#EchoError("Error: Specify host.")
    return
  endif

  if (a:port == '')
    call eclim#util#EchoError("Error: Specify port.")
    return
  endif

  let file = eclim#lang#SilentUpdate()

  let command = s:command_start
  let command = substitute(command, '<target_name>', file, '')
  let command = substitute(command, '<host>', a:host, '')
  let command = substitute(command, '<port>', a:port, '')
  let command = substitute(command, '<vim_servername>', v:servername, '')

  let result = eclim#Execute(command)
endfunction " }}}

function! eclim#java#debug#DebugControl(action) " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let file = eclim#lang#SilentUpdate()

  let command = s:command_control
  let command = substitute(command, '<action>', a:action, '')

  let result = eclim#Execute(command)

  if ((a:action == 'stop') || (a:action == 'terminate'))
    " TODO Check if defined before undefining
    "call eclim#display#signs#Undefine(s:breakpoint_sign_name)
  endif
endfunction " }}}

function! eclim#java#debug#Breakpoint(action) " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let command = s:command_breakpoint
  let command = substitute(command, '<action>', a:action, '')

  call eclim#java#debug#DisplayPositions(eclim#Execute(command))
endfunction " }}}

function! eclim#java#debug#DisplayPositions(results) " {{{
  if (type(a:results) != g:LIST_TYPE)
    return
  endif

  if empty(a:results)
    echo "No breakpoints"
    return
  endif

  call eclim#util#SetLocationList(eclim#util#ParseLocationEntries(a:results))
  let locs = getloclist(0)
  exec 'lopen ' . g:EclimLocationListHeight
endfunction " }}}

function! eclim#java#debug#ToggleBreakpoint() " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let project = eclim#project#util#GetCurrentProjectName()
  let file = eclim#lang#SilentUpdate()
  let line_num = line('.')

  let command = s:command_toggle_breakpoint
  let command = substitute(command, '<project>', project, '')
  let command = substitute(command, '<file>', file, '')
  let command = substitute(command, '<line_num>', line_num, '')

  let result = eclim#Execute(command)

  call eclim#display#signs#Define(s:breakpoint_sign_name, '*', '')
  call eclim#display#signs#Toggle(s:breakpoint_sign_name, line_num)
endfunction " }}}

function! eclim#java#debug#Step(action) " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let command = s:command_step
  let command = substitute(command, '<action>', a:action, '')

  let result = eclim#Execute(command)
  call eclim#util#Echo(string(result))
endfunction " }}}

function! eclim#java#debug#Vars() " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let command = s:command_vars

  let results = eclim#Execute(command)

  let window_name = "Debug Variables"
  let filename = expand('%:p')
  call eclim#util#TempWindowClear(window_name)

  call eclim#util#TempWindow(
        \ window_name, results, {'height': g:EclimLocationListHeight})

  setlocal foldmethod=expr
  setlocal foldexpr=eclim#display#fold#GetNeatFold(v:lnum)
  setlocal foldtext=eclim#display#fold#NeatFoldText()

  augroup temp_window
    autocmd! BufWinLeave <buffer>
    call eclim#util#GoToBufferWindowRegister(filename)
  augroup END
endfunction " }}}

function! eclim#java#debug#StackFrame() " {{{
  if !eclim#project#util#IsCurrentFileInProject()
    return
  endif

  let command = s:command_stackframe
  let results = eclim#Execute(command)

  if empty(results)
    echo "No stack frames"
    return
  endif

  let window_name = "Stack Frames"
  let filename = expand('%:p')
  call eclim#util#TempWindowClear(window_name)

  call eclim#util#TempWindow(
        \ window_name, results, {'height': g:EclimLocationListHeight})

  setlocal foldmethod=expr
  setlocal foldexpr=eclim#display#fold#GetNeatFold(v:lnum)
  setlocal foldtext=eclim#display#fold#NeatFoldText()

  augroup temp_window
    autocmd! BufWinLeave <buffer>
    call eclim#util#GoToBufferWindowRegister(filename)
  augroup END
endfunction " }}}

" vim:ft=vim:fdm=marker

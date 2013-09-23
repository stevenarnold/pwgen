## NAME

`pwgen` - a utility for generating passwords based on different policies

## SYNPOSIS

`pwgen [generate|fetch] <options>`

## DESCRIPTION

pwgen is a command-line tool for generating passwords according to different policies.  Using command-line options, many different policies can be used, including such options as password length, use of numeric digits, capital letters, and punctuation.  Users have the option of saving their passwords, along with metadata, by associating it with a tag, which can be retrieved with the entire tag or part of it.  Users can opt, when retrieving a password, to send it directly to their clipboard for pasting into an application, such as a web browser.

Metadata can be tagged.  By default the information selected from a record is the password, but the user can optionally choose other data, and in fact the user is not required to store a password field at all.  This may be useful for storing information such as a credit card number, billing address, etc.

User data is stored on disk using strong encryption.  The user may set the caching policy, up to and including immediate removal of the keystore password for each usage.

## INSTALLATION

Download from https://github.com/stevenarnold/pwgen.

## ENVIRONMENT

pwgen requires a JVM version 1.6 or higher.  It can be run as follows:

`$ java -jar pwgen.jar [args]`

## OPTIONS

Basic subcommands:

  * `generate [options] [pairs]`
    This will generate a password using the given options.  If a profile is specified, the options given on the command-line will override any options in that profile with the ones provided.  If the options include an assertion to save the profile, any provided pairs will be saved with it.  If specified in the options, the generated password will be pushed to the clipboard.
    * `-m <number>` or `--max <number>`
      The maximum number of characters allowed for the password.
    * `-n <number>` or `--min <number>`
      The minimum number of characters allowed for the password.
    * `-md <number>` or `--max-numbers <number>`
      The maximum number of numeric digits in the password.  If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be numeric.  If `--max-numbers` is less than `--min-numbers`, pwgen displays an error and quits.
    * `-nd <number>` or `--min-numbers <number>`
      The minimum number of numeric digits in the password.  See also the description of `--max-numbers`.
    * `-mc <number>` or `--max-capitals <number>`
      The maximum number of capital letters to use in the password.    If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be capital letters.  If `--max-capitals` is less than `--min-capitals`, pwgen displays an error and quits.
    * `-nc <number>` or `--min-capitals <number>`
      The minimum number of capitals letters to use in the password.  See also the description of `--max-capitals`.
    * `-ms <number>` or `--max-special <number>`
      The maximum number of 'special' characters to use in the password.  Special characters are punctuation symbols on a standard U.S. keyboard and do not include Unicode characters such as umlauts or accented characters.  If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be special characters.  If `--max-special` is less than `--min-special`, pwgen displays an error and quits.
    * `-ns <number>` or `--min-special <number>`
      The minimum number of 'special' characters to use in the password.  See also the description of `--max-special`.
    * `-sc <charset>` or `--special-charset <charset>`
      Use the given string as the "special" or punctuation characters in the generated password, instead of the standard charset 'special'.  Other standard charset names (such as 'numeric') may also be used.
    * `-ic <charset>` or `--initial-charset <charset>`
      Require that a character in the given string be the initial character of the password.  Other standard charset names (such as 'alpha') may also be used.
    * `-ec <charset>` or `--ending-charset <charset>`
      Require that a character in the given string be the ending or final character of the password.  Other standard charset names (such as 'alphanumeric') may also be used.

>   **--charset [tag] [names] &lt;charset&gt;**: Define a character set, optionally naming it with the given tag.  For example, your organization may limit special characters to underscore, dash and period, but allow alphanumeric characters.  If you wished to limit your password to these characters, you could use the syntax --charset alphanumeric '_-.'  If you define --special-charset, that value will be merged with the --charset value.
<br /><br />
>   **--weighting &lt;lower-value&gt;,&lt;upper-value&gt;,&lt;numeric-value&gt;,&lt;special-value&gt;**: A value representing the weighting of charsets in password selection.  This setting allows the user to adjust the preference for different charsets.  By default, the charsets are, in order, alpha-lower, alpha-upper, numeric, and special, and the default weighting for these character sets is 15,1,1,1.  If you use the --special-charset flag, your set of special characters will replace the default set.  You may, however, specify the names of charsets and their weights with a &lt;name&gt;:&lt;value&gt; syntax, separated by commas.  This includes charsets that you define with the --charset flag.  The &lt;name&gt;:&lt;value&gt; format may be included after the four default values, or the default values may be removed entirely and only the &lt;name&gt;:&lt;value&gt; syntax used.  If this flag is used, only charsets specified in the weighting will be used in selecting a password.  'min' and 'max' flags take precedence over weighting, if used together.  For example, if a user specifies '--weighting 10,2,0,0', ordinarily numeric digits and special characters would not be used.  But if '--min-numbers 3' was also specified, the resulting password would have at least three numeric digits, despite the weighting.
<br /><br />
>   **--create-profile &lt;name&gt; or -cp**: Save this profile with this particular set of options.  If the profile already exists, it will be overwritten by the options given in this command.  The user must provide a password to use this option, either to encrypt a new password profile database, or decrypt the existing one for modification.  See --password.
<br /><br />
>   **--password or -p**: Use the given password to modify or use a profile.  Default is to ask the user interactively in a way that does not echo the password to the screen.
<br /><br />
>   **--use-profile &lt;name&gt; or -up**: Use the specified profile to generate a password.  If additional options are given, they will supercede the profile's options.
<br /><br />
>   **--allow-spaces or -as**: Include the space character in the charset used to pick passwords, and do not remove spaces if a dictionary word contains one.  However, a space cannot be the first or last character of a password.
<br /><br />
>   **--memorable &lt;pct&gt; or -r**: If an alphabetic (upper or lower) character is randomly chosen, there is a 'pct' chance that it will be replaced by a whole or partial dictionary word.  This will lengthen the candidate password by (most likely) more than one character.  If the --weighting flag was used, the characters in the selected word or word fragment will be subject to the weighting rules and may be randomly transformed into other character classes depending on the weighting rules.
<br /><br />
>   **--dict or -d**: Use the given file as the dictionary for memorable passwords, one word per line.  This option has no effect if the '--memorable' flag is not used.
    
- fetch [options] &lt;profile-tag&gt;[:meta-tag]: This will fetch either the password for the given profile, or the data associated with the given meta-tag in the profile.  If the password for the profile is not cached, the user will be asked for the password.

(defrecord+defaults PasswordPreferences
  [at-least                15        ;; Password must be at least this long
   at-most                 25        ;; And no longer than this
   lower-alpha-weight      20        ;; Weighting for lowercase characters
   upper-alpha-weight       4        ;; Weighting for uppercase characters
   avoid-shift-pct         10        ;; Percentage chance we'll use a shifted char
                                     ;;     if one is picked
   use-at-least-upper       1        ;; Use at least this many uppercase chars.
                                     ;;     All use-at-least values must be < at-least
   numeric-weight           4        ;; Weighting for numeric characters
   use-at-least-numeric     1        ;; Use at least this many numeric chars
   special-weight           0        ;; Weighting for special chars
   use-at-least-special     0        ;; Use at least this many special characters
   allow-spaces             false    ;; Allow spaces to be in password
   make-memorable-pct       0]       ;; Percentage chance we'll use a dictionary 
                                     ;;     word when an alpha char would have been
                                     ;;     picked

## Examples

...

### Bugs

...

## License

Copyright © 2013 Arnold Software Associates and Steven D. Arnold

Distributed under the Eclipse Public License, the same as Clojure.

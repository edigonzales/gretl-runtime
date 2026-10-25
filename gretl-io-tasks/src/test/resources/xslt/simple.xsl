<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8"/>
  <xsl:template match="/">
    <result>
      <xsl:value-of select="/root/value"/>
    </result>
  </xsl:template>
</xsl:stylesheet>
